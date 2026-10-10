import os
import re
import secrets
from datetime import date, datetime, timedelta
from urllib.parse import urlparse

from flask import Flask, abort, flash, redirect, render_template, request, url_for
from flask_login import LoginManager, current_user, login_user, logout_user
from sqlalchemy import func, inspect, text

from models import (
    DIFFICULTY_LABELS,
    DIFFICULTY_REWARDS,
    WEEKDAY_LABELS,
    InventoryItem,
    RecurringCompletion,
    RecurringTask,
    ShopItem,
    Task,
    Transaction,
    User,
    db,
)

from pricing import DIAS_PARA_ESQUECER, calcular_preco

BASE_DIR = os.path.abspath(os.path.dirname(__file__))


def load_secret_key():
    """Usa SECRET_KEY do ambiente; senão gera uma vez e guarda em .secret_key."""
    if os.environ.get("SECRET_KEY"):
        return os.environ["SECRET_KEY"]
    path = os.path.join(BASE_DIR, ".secret_key")
    if not os.path.exists(path):
        with open(path, "w") as f:
            f.write(secrets.token_hex(32))
    with open(path) as f:
        return f.read().strip()


app = Flask(__name__)
app.config["SECRET_KEY"] = load_secret_key()
app.config["SQLALCHEMY_DATABASE_URI"] = "sqlite:///" + os.path.join(BASE_DIR, "gold_quest.db")
app.config["SESSION_COOKIE_SAMESITE"] = "Lax"
app.config["REMEMBER_COOKIE_SAMESITE"] = "Lax"
app.config["REMEMBER_COOKIE_DURATION"] = timedelta(days=60)
db.init_app(app)

login_manager = LoginManager(app)
login_manager.login_view = "login"
login_manager.login_message = None  # a tela de login já se explica

# Tabelas que pertencem a um usuário (todas ganham a coluna user_id)
OWNED_TABLES = ["task", "recurring_task", "shop_item", "inventory_item", "transaction"]
PUBLIC_ENDPOINTS = {"login", "register", "static"}
USERNAME_RE = re.compile(r"^[a-z0-9._-]{3,30}$")
MIN_PASSWORD = 8


@login_manager.user_loader
def load_user(user_id):
    return db.session.get(User, int(user_id))


@app.before_request
def require_login():
    if request.endpoint in PUBLIC_ENDPOINTS or current_user.is_authenticated:
        return None
    return login_manager.unauthorized()


# ---------- Helpers ----------

def get_owned(model, obj_id):
    """Busca um registro do usuário logado; 404 se não existir ou for de outra pessoa."""
    obj = db.get_or_404(model, obj_id)
    if obj.user_id != current_user.id:
        abort(404)
    return obj


def change_gold(amount, description):
    """Altera o saldo e registra no histórico. Quem chama faz o commit."""
    current_user.gold += amount
    db.session.add(Transaction(user_id=current_user.id, amount=amount, description=description))


def seed_shop(user):
    db.session.add_all([
        ShopItem(user_id=user.id, name="1 hora de videogame", price=100, emoji="🎮"),
        ShopItem(user_id=user.id, name="Episódio de série", price=50, emoji="📺"),
        ShopItem(user_id=user.id, name="Pedir delivery", price=300, emoji="🍕"),
    ])


def upgrade_db():
    """Adiciona colunas novas em bancos criados por versões anteriores."""
    insp = inspect(db.engine)
    inv_cols = {c["name"] for c in insp.get_columns("inventory_item")}
    shop_cols = {c["name"] for c in insp.get_columns("shop_item")}
    with db.engine.begin() as conn:
        if "created_at" not in shop_cols:
            conn.execute(text("ALTER TABLE shop_item ADD COLUMN created_at DATETIME"))
            conn.execute(text("UPDATE shop_item SET created_at = :agora"),
                         {"agora": datetime.now().isoformat(sep=" ")})
        if "price_paid" not in inv_cols:
            conn.execute(text("ALTER TABLE inventory_item ADD COLUMN price_paid INTEGER"))
        if "shop_item_id" not in inv_cols:
            conn.execute(text(
                "ALTER TABLE inventory_item ADD COLUMN shop_item_id INTEGER REFERENCES shop_item(id)"
            ))
            # Liga compras antigas ao item da loja pelo nome, para aproveitar o histórico
            conn.execute(text(
                "UPDATE inventory_item SET shop_item_id = "
                "(SELECT id FROM shop_item WHERE shop_item.name = inventory_item.name) "
                "WHERE shop_item_id IS NULL"
            ))
        # Versões de um usuário só não tinham dono nos registros
        for table in OWNED_TABLES:
            if "user_id" not in {c["name"] for c in insp.get_columns(table)}:
                conn.execute(text(
                    f'ALTER TABLE "{table}" ADD COLUMN user_id INTEGER REFERENCES "user"(id)'
                ))
                conn.execute(text(f'CREATE INDEX ix_{table}_user_id ON "{table}" (user_id)'))


def claim_legacy_data(user):
    """Entrega ao primeiro usuário os dados da época em que o app não tinha login."""
    claimed = False
    for table in OWNED_TABLES:
        result = db.session.execute(
            text(f'UPDATE "{table}" SET user_id = :uid WHERE user_id IS NULL'), {"uid": user.id}
        )
        claimed = claimed or result.rowcount > 0
    if inspect(db.engine).has_table("wallet"):
        old_gold = db.session.execute(text("SELECT gold FROM wallet WHERE id = 1")).scalar()
        if old_gold:
            user.gold = old_gold
            claimed = True
    return claimed


def safe_next(target):
    """Só aceita redirecionar para caminhos deste próprio site."""
    if not target:
        return None
    parsed = urlparse(target)
    if parsed.scheme or parsed.netloc or not target.startswith("/") or target.startswith("//") \
            or "\\" in target:
        return None
    return target


def preco_atual(item, hoje=None):
    hoje = hoje or date.today()
    desde = datetime.combine(hoje, datetime.min.time()) - timedelta(days=60)
    compras = [
        c.purchased_at
        for c in InventoryItem.query.filter(
            InventoryItem.shop_item_id == item.id,
            InventoryItem.purchased_at >= desde,
        )
    ]
    if not compras:
        # Sem compras recentes: usa a última compra de todas, se existir
        ultima = (
            InventoryItem.query.filter_by(shop_item_id=item.id)
            .order_by(InventoryItem.purchased_at.desc())
            .first()
        )
        if ultima:
            compras = [ultima.purchased_at]
    return calcular_preco(item.price, compras, item.created_at, hoje)


with app.app_context():
    db.create_all()
    upgrade_db()


@app.context_processor
def inject_globals():
    gold = current_user.gold if current_user.is_authenticated else None
    return {"gold": gold}


@app.template_filter("br_date")
def br_date(value):
    return value.strftime("%d/%m") if value else ""


@app.template_filter("br_datetime")
def br_datetime(value):
    return value.strftime("%d/%m às %H:%M") if value else ""


# ---------- Conta ----------

@app.route("/entrar", methods=["GET", "POST"])
def login():
    if current_user.is_authenticated:
        return redirect(url_for("tasks"))
    next_url = safe_next(request.values.get("next"))

    if request.method == "POST":
        username = request.form.get("username", "").strip().lower()
        password = request.form.get("password", "")
        user = User.query.filter_by(username=username).first()
        if user is None or not user.check_password(password):
            flash("Usuário ou senha incorretos.", "error")
            return render_template("login.html", next_url=next_url, username=username), 401
        login_user(user, remember=bool(request.form.get("remember")))
        return redirect(next_url or url_for("tasks"))

    return render_template("login.html", next_url=next_url, username="")


@app.route("/cadastro", methods=["GET", "POST"])
def register():
    if current_user.is_authenticated:
        return redirect(url_for("tasks"))

    if request.method == "POST":
        username = request.form.get("username", "").strip().lower()
        password = request.form.get("password", "")
        confirm = request.form.get("confirm", "")

        error = None
        if not USERNAME_RE.match(username):
            error = "O usuário deve ter de 3 a 30 caracteres: letras, números, ponto, hífen ou _."
        elif User.query.filter_by(username=username).first():
            error = "Esse nome de usuário já existe."
        elif len(password) < MIN_PASSWORD:
            error = f"A senha precisa ter pelo menos {MIN_PASSWORD} caracteres."
        elif password != confirm:
            error = "As senhas não conferem."
        if error:
            flash(error, "error")
            return render_template("register.html", username=username), 400

        first_user = User.query.count() == 0
        user = User(username=username)
        user.set_password(password)
        db.session.add(user)
        db.session.flush()  # gera o id

        claimed = first_user and claim_legacy_data(user)
        if not claimed:
            seed_shop(user)
        db.session.commit()

        login_user(user, remember=True)
        if claimed:
            flash("Conta criada. Seus dados anteriores foram transferidos para ela.", "ok")
        else:
            flash(f"Bem-vindo, {user.username}! Conclua tarefas para ganhar gold.", "ok")
        return redirect(url_for("tasks"))

    return render_template("register.html", username="")


@app.post("/sair")
def logout():
    logout_user()
    return redirect(url_for("login"))


# ---------- Tarefas ----------

@app.route("/")
def tasks():
    pending = (
        Task.query.filter_by(user_id=current_user.id, done=False)
        .order_by(Task.due_date.is_(None), Task.due_date, Task.created_at)
        .all()
    )
    done = (
        Task.query.filter_by(user_id=current_user.id, done=True)
        .order_by(Task.completed_at.desc())
        .limit(10)
        .all()
    )
    today = date.today()
    recurring = (
        RecurringTask.query.filter_by(user_id=current_user.id)
        .order_by(RecurringTask.created_at)
        .all()
    )
    today_habits = [r for r in recurring if r.is_scheduled(today)]
    return render_template(
        "tasks.html",
        pending=pending,
        done=done,
        recurring=recurring,
        today_habits=today_habits,
        today_done=sum(1 for r in today_habits if r.is_done_on(today)),
        weekday_labels=WEEKDAY_LABELS,
        today=today,
        labels=DIFFICULTY_LABELS,
        rewards=DIFFICULTY_REWARDS,
    )


@app.post("/tarefas")
def add_task():
    title = request.form.get("title", "").strip()
    difficulty = request.form.get("difficulty", "media")
    due_raw = request.form.get("due_date", "").strip()

    if not title:
        flash("Escreva um título para a tarefa.", "error")
        return redirect(url_for("tasks"))
    if difficulty not in DIFFICULTY_REWARDS:
        difficulty = "media"

    # Se marcou dias da semana, vira tarefa recorrente
    weekdays = sorted({int(d) for d in request.form.getlist("weekdays") if d.isdigit() and 0 <= int(d) <= 6})
    if weekdays:
        db.session.add(RecurringTask(
            user_id=current_user.id,
            title=title[:120],
            difficulty=difficulty,
            weekdays=",".join(str(d) for d in weekdays),
        ))
        db.session.commit()
        flash("Tarefa recorrente adicionada.", "ok")
        return redirect(url_for("tasks"))

    due_date = None
    if due_raw:
        try:
            due_date = date.fromisoformat(due_raw)
        except ValueError:
            flash("Data de prazo inválida.", "error")
            return redirect(url_for("tasks"))

    db.session.add(Task(
        user_id=current_user.id, title=title[:120], difficulty=difficulty, due_date=due_date
    ))
    db.session.commit()
    flash("Tarefa adicionada.", "ok")
    return redirect(url_for("tasks"))


@app.post("/tarefas/<int:task_id>/concluir")
def complete_task(task_id):
    task = get_owned(Task, task_id)
    if task.done:
        return redirect(url_for("tasks"))

    reward = task.calculate_reward(date.today())
    task.done = True
    task.completed_at = datetime.now()
    task.reward_earned = reward
    change_gold(reward, f"Tarefa concluída: {task.title}")
    db.session.commit()

    flash(f"+{reward} gold por concluir “{task.title}”", "gold")
    return redirect(url_for("tasks"))


@app.post("/tarefas/<int:task_id>/excluir")
def delete_task(task_id):
    task = get_owned(Task, task_id)
    db.session.delete(task)
    db.session.commit()
    flash("Tarefa excluída.", "ok")
    return redirect(url_for("tasks"))


# ---------- Recorrentes ----------

@app.post("/recorrentes/<int:rec_id>/concluir")
def complete_recurring(rec_id):
    rec = get_owned(RecurringTask, rec_id)
    today = date.today()

    if not rec.is_scheduled(today):
        flash(f"“{rec.title}” não está programada para hoje.", "error")
        return redirect(url_for("tasks"))
    if rec.is_done_on(today):
        return redirect(url_for("tasks"))

    reward = rec.reward()
    db.session.add(RecurringCompletion(task=rec, date=today, reward=reward))
    change_gold(reward, f"Recorrente concluída: {rec.title}")
    db.session.commit()

    flash(f"+{reward} gold por “{rec.title}”", "gold")
    return redirect(url_for("tasks"))


@app.post("/recorrentes/<int:rec_id>/excluir")
def delete_recurring(rec_id):
    rec = get_owned(RecurringTask, rec_id)
    db.session.delete(rec)
    db.session.commit()
    flash("Tarefa recorrente excluída.", "ok")
    return redirect(url_for("tasks"))


@app.route("/semana")
def week():
    recurring = (
        RecurringTask.query.filter_by(user_id=current_user.id)
        .order_by(RecurringTask.title)
        .all()
    )
    days = []
    for i, label in enumerate(WEEKDAY_LABELS):
        tasks_day = [r for r in recurring if i in r.weekday_list]
        days.append({
            "index": i,
            "label": label,
            "count": len(tasks_day),
            "gold": sum(r.reward() for r in tasks_day),
        })
    max_count = max((d["count"] for d in days), default=0)
    return render_template(
        "week.html",
        recurring=recurring,
        days=days,
        max_count=max_count,
        today_index=date.today().weekday(),
        labels=DIFFICULTY_LABELS,
    )


# ---------- Loja ----------

@app.route("/loja")
def shop():
    hoje = date.today()
    items = ShopItem.query.filter_by(user_id=current_user.id).order_by(ShopItem.price).all()
    offers = [(item, preco_atual(item, hoje)) for item in items]
    return render_template("shop.html", offers=offers, dias_esquecer=DIAS_PARA_ESQUECER)


@app.post("/loja")
def add_shop_item():
    name = request.form.get("name", "").strip()
    emoji = request.form.get("emoji", "").strip() or "🎁"
    try:
        price = int(request.form.get("price", 0))
    except ValueError:
        price = 0

    if not name or price <= 0:
        flash("Dê um nome e um preço maior que zero para a recompensa.", "error")
        return redirect(url_for("shop"))

    db.session.add(ShopItem(user_id=current_user.id, name=name[:80], price=price, emoji=emoji[:8]))
    db.session.commit()
    flash(f"{name} agora está na loja.", "ok")
    return redirect(url_for("shop"))


@app.post("/loja/<int:item_id>/comprar")
def buy_item(item_id):
    item = get_owned(ShopItem, item_id)

    price = preco_atual(item).price  # sempre recalcula na hora da compra

    if current_user.gold < price:
        flash(f"Faltam {price - current_user.gold} gold para comprar {item.name}.", "error")
        return redirect(url_for("shop"))

    change_gold(-price, f"Compra: {item.name}")
    db.session.add(InventoryItem(
        user_id=current_user.id, name=item.name, emoji=item.emoji,
        shop_item_id=item.id, price_paid=price,
    ))
    db.session.commit()
    flash(f"{item.name} foi para o seu inventário por {price} gold.", "ok")
    return redirect(url_for("shop"))


@app.post("/loja/<int:item_id>/excluir")
def delete_shop_item(item_id):
    item = get_owned(ShopItem, item_id)
    # Mantém o inventário, só desfaz a ligação com o item removido
    InventoryItem.query.filter_by(shop_item_id=item.id).update({"shop_item_id": None})
    db.session.delete(item)
    db.session.commit()
    flash("Recompensa removida da loja.", "ok")
    return redirect(url_for("shop"))


# ---------- Inventário ----------

@app.route("/inventario")
def inventory():
    mine = InventoryItem.query.filter_by(user_id=current_user.id)
    available = mine.filter_by(used_at=None).order_by(InventoryItem.purchased_at).all()
    used = (
        mine.filter(InventoryItem.used_at.isnot(None))
        .order_by(InventoryItem.used_at.desc())
        .limit(10)
        .all()
    )
    return render_template("inventory.html", available=available, used=used)


@app.post("/inventario/<int:inv_id>/usar")
def use_item(inv_id):
    inv = get_owned(InventoryItem, inv_id)
    if inv.used_at is None:
        inv.used_at = datetime.now()
        db.session.commit()
        flash(f"Aproveite: {inv.name}!", "ok")
    return redirect(url_for("inventory"))


# ---------- Histórico ----------

@app.route("/historico")
def history():
    mine = Transaction.query.filter_by(user_id=current_user.id)
    transactions = mine.order_by(Transaction.created_at.desc()).limit(100).all()
    earned = mine.filter(Transaction.amount > 0).with_entities(func.sum(Transaction.amount)).scalar() or 0
    spent = -(mine.filter(Transaction.amount < 0).with_entities(func.sum(Transaction.amount)).scalar() or 0)
    return render_template("history.html", transactions=transactions, earned=earned, spent=spent)


if __name__ == "__main__":
    # host="0.0.0.0" deixa você abrir no celular pela rede local.
    # O modo debug fica desligado por padrão (é inseguro aberto na rede);
    # para desenvolver, defina a variável FLASK_DEBUG=1.
    debug = os.environ.get("FLASK_DEBUG") == "1"
    app.run(debug=debug, host="0.0.0.0", port=5000)
