import os
from datetime import date, datetime, timedelta

from flask import Flask, flash, redirect, render_template, request, url_for
from sqlalchemy import inspect, text

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
    Wallet,
    db,
)

from pricing import DIAS_PARA_ESQUECER, calcular_preco

BASE_DIR = os.path.abspath(os.path.dirname(__file__))

app = Flask(__name__)
app.config["SECRET_KEY"] = os.environ.get("SECRET_KEY", "troque-esta-chave-em-producao")
app.config["SQLALCHEMY_DATABASE_URI"] = "sqlite:///" + os.path.join(BASE_DIR, "gold_quest.db")
db.init_app(app)


# ---------- Helpers ----------

def get_wallet():
    wallet = db.session.get(Wallet, 1)
    if wallet is None:
        wallet = Wallet(id=1, gold=0)
        db.session.add(wallet)
        db.session.commit()
    return wallet


def change_gold(amount, description):
    """Altera o saldo e registra no histórico. Quem chama faz o commit."""
    wallet = get_wallet()
    wallet.gold += amount
    db.session.add(Transaction(amount=amount, description=description))


def seed_shop():
    if ShopItem.query.count() == 0:
        db.session.add_all([
            ShopItem(name="1 hora de videogame", price=100, emoji="🎮"),
            ShopItem(name="Episódio de série", price=50, emoji="📺"),
            ShopItem(name="Pedir delivery", price=300, emoji="🍕"),
        ])
        db.session.commit()


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
    get_wallet()
    seed_shop()


@app.context_processor
def inject_globals():
    return {"gold": get_wallet().gold}


@app.template_filter("br_date")
def br_date(value):
    return value.strftime("%d/%m") if value else ""


@app.template_filter("br_datetime")
def br_datetime(value):
    return value.strftime("%d/%m às %H:%M") if value else ""


# ---------- Tarefas ----------

@app.route("/")
def tasks():
    pending = (
        Task.query.filter_by(done=False)
        .order_by(Task.due_date.is_(None), Task.due_date, Task.created_at)
        .all()
    )
    done = (
        Task.query.filter_by(done=True)
        .order_by(Task.completed_at.desc())
        .limit(10)
        .all()
    )
    today = date.today()
    recurring = RecurringTask.query.order_by(RecurringTask.created_at).all()
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

    db.session.add(Task(title=title[:120], difficulty=difficulty, due_date=due_date))
    db.session.commit()
    flash("Tarefa adicionada.", "ok")
    return redirect(url_for("tasks"))


@app.post("/tarefas/<int:task_id>/concluir")
def complete_task(task_id):
    task = db.get_or_404(Task, task_id)
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
    task = db.get_or_404(Task, task_id)
    db.session.delete(task)
    db.session.commit()
    flash("Tarefa excluída.", "ok")
    return redirect(url_for("tasks"))


# ---------- Recorrentes ----------

@app.post("/recorrentes/<int:rec_id>/concluir")
def complete_recurring(rec_id):
    rec = db.get_or_404(RecurringTask, rec_id)
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
    rec = db.get_or_404(RecurringTask, rec_id)
    db.session.delete(rec)
    db.session.commit()
    flash("Tarefa recorrente excluída.", "ok")
    return redirect(url_for("tasks"))


@app.route("/semana")
def week():
    recurring = RecurringTask.query.order_by(RecurringTask.title).all()
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
    items = ShopItem.query.order_by(ShopItem.price).all()
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

    db.session.add(ShopItem(name=name[:80], price=price, emoji=emoji[:8]))
    db.session.commit()
    flash(f"{name} agora está na loja.", "ok")
    return redirect(url_for("shop"))


@app.post("/loja/<int:item_id>/comprar")
def buy_item(item_id):
    item = db.get_or_404(ShopItem, item_id)
    wallet = get_wallet()

    price = preco_atual(item).price  # sempre recalcula na hora da compra

    if wallet.gold < price:
        flash(f"Faltam {price - wallet.gold} gold para comprar {item.name}.", "error")
        return redirect(url_for("shop"))

    change_gold(-price, f"Compra: {item.name}")
    db.session.add(InventoryItem(
        name=item.name, emoji=item.emoji, shop_item_id=item.id, price_paid=price
    ))
    db.session.commit()
    flash(f"{item.name} foi para o seu inventário por {price} gold.", "ok")
    return redirect(url_for("shop"))


@app.post("/loja/<int:item_id>/excluir")
def delete_shop_item(item_id):
    item = db.get_or_404(ShopItem, item_id)
    # Mantém o inventário, só desfaz a ligação com o item removido
    InventoryItem.query.filter_by(shop_item_id=item.id).update({"shop_item_id": None})
    db.session.delete(item)
    db.session.commit()
    flash("Recompensa removida da loja.", "ok")
    return redirect(url_for("shop"))


# ---------- Inventário ----------

@app.route("/inventario")
def inventory():
    available = InventoryItem.query.filter_by(used_at=None).order_by(InventoryItem.purchased_at).all()
    used = (
        InventoryItem.query.filter(InventoryItem.used_at.isnot(None))
        .order_by(InventoryItem.used_at.desc())
        .limit(10)
        .all()
    )
    return render_template("inventory.html", available=available, used=used)


@app.post("/inventario/<int:inv_id>/usar")
def use_item(inv_id):
    inv = db.get_or_404(InventoryItem, inv_id)
    if inv.used_at is None:
        inv.used_at = datetime.now()
        db.session.commit()
        flash(f"Aproveite: {inv.name}!", "ok")
    return redirect(url_for("inventory"))


# ---------- Histórico ----------

@app.route("/historico")
def history():
    transactions = Transaction.query.order_by(Transaction.created_at.desc()).limit(100).all()
    earned = sum(t.amount for t in Transaction.query.filter(Transaction.amount > 0))
    spent = -sum(t.amount for t in Transaction.query.filter(Transaction.amount < 0))
    return render_template("history.html", transactions=transactions, earned=earned, spent=spent)


if __name__ == "__main__":
    # host="0.0.0.0" deixa você abrir no celular pela rede local.
    # O modo debug fica desligado por padrão (é inseguro aberto na rede);
    # para desenvolver, defina a variável FLASK_DEBUG=1.
    debug = os.environ.get("FLASK_DEBUG") == "1"
    app.run(debug=debug, host="0.0.0.0", port=5000)
