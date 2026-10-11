from datetime import datetime, timedelta

from flask_login import UserMixin
from flask_sqlalchemy import SQLAlchemy
from werkzeug.security import check_password_hash, generate_password_hash

db = SQLAlchemy()

# Quanto cada dificuldade paga de gold
DIFFICULTY_REWARDS = {"facil": 10, "media": 30, "dificil": 60}
DIFFICULTY_LABELS = {"facil": "Fácil", "media": "Média", "dificil": "Difícil"}

ON_TIME_BONUS = 1.2   # +20% se concluir até o prazo
LATE_PENALTY = 0.5    # metade do gold se concluir atrasado
MISSED_PENALTY = 0.10  # tarefa com penalidade não feita: perde 10% do valor base
REVIEW_MAX_DAYS = 7    # a revisão diária olha no máximo uma semana para trás


def penalty_for(difficulty):
    return max(1, round(DIFFICULTY_REWARDS.get(difficulty, 10) * MISSED_PENALTY))


class User(UserMixin, db.Model):
    id = db.Column(db.Integer, primary_key=True)
    username = db.Column(db.String(30), nullable=False, unique=True)  # sempre minúsculo
    password_hash = db.Column(db.String(255), nullable=False)
    gold = db.Column(db.Integer, nullable=False, default=0)
    created_at = db.Column(db.DateTime, nullable=False, default=datetime.now)
    # Último dia já revisado (ver review.py); None = nunca revisou
    last_review_date = db.Column(db.Date, nullable=True)

    def add_gold(self, amount, description):
        """Altera o saldo e registra no histórico. Quem chama faz o commit."""
        self.gold += amount
        db.session.add(Transaction(user_id=self.id, amount=amount, description=description))

    def set_password(self, password):
        self.password_hash = generate_password_hash(password)

    def check_password(self, password):
        return check_password_hash(self.password_hash, password)


def user_fk():
    """Coluna de dono, repetida em toda tabela que pertence a um usuário."""
    return db.Column(db.Integer, db.ForeignKey("user.id"), nullable=False, index=True)


class Task(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    user_id = user_fk()
    title = db.Column(db.String(120), nullable=False)
    difficulty = db.Column(db.String(10), nullable=False, default="media")
    due_date = db.Column(db.Date, nullable=True)
    done = db.Column(db.Boolean, nullable=False, default=False)
    created_at = db.Column(db.DateTime, nullable=False, default=datetime.now)
    completed_at = db.Column(db.DateTime, nullable=True)
    reward_earned = db.Column(db.Integer, nullable=True)
    penalize = db.Column(db.Boolean, nullable=False, default=False)
    penalized_at = db.Column(db.Date, nullable=True)  # quando a penalidade foi cobrada

    def penalty(self):
        return penalty_for(self.difficulty)

    def calculate_reward(self, today):
        base = DIFFICULTY_REWARDS.get(self.difficulty, 10)
        if self.due_date is None:
            return base
        if today <= self.due_date:
            return round(base * ON_TIME_BONUS)
        return max(1, round(base * LATE_PENALTY))

    def due_status(self, today):
        if self.due_date is None:
            return None
        if today > self.due_date:
            return "late"
        if today == self.due_date:
            return "today"
        return "future"


class ShopItem(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    user_id = user_fk()
    name = db.Column(db.String(80), nullable=False)
    price = db.Column(db.Integer, nullable=False)  # preço base
    emoji = db.Column(db.String(8), nullable=False, default="🎁")
    created_at = db.Column(db.DateTime, nullable=True, default=datetime.now)


class InventoryItem(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    user_id = user_fk()
    shop_item_id = db.Column(db.Integer, db.ForeignKey("shop_item.id"), nullable=True)
    price_paid = db.Column(db.Integer, nullable=True)
    name = db.Column(db.String(80), nullable=False)
    emoji = db.Column(db.String(8), nullable=False, default="🎁")
    purchased_at = db.Column(db.DateTime, nullable=False, default=datetime.now)
    used_at = db.Column(db.DateTime, nullable=True)


class Transaction(db.Model):
    """Histórico de tudo que entra (+) e sai (-) da carteira."""
    id = db.Column(db.Integer, primary_key=True)
    user_id = user_fk()
    amount = db.Column(db.Integer, nullable=False)
    description = db.Column(db.String(160), nullable=False)
    created_at = db.Column(db.DateTime, nullable=False, default=datetime.now)


WEEKDAY_LABELS = ["Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"]  # date.weekday(): 0 = segunda
MONTH_LABELS = ["janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho",
                "agosto", "setembro", "outubro", "novembro", "dezembro"]


def month_bounds(day):
    """Primeiro e último dia do mês de `day`."""
    first = day.replace(day=1)
    next_first = (first + timedelta(days=32)).replace(day=1)
    return first, next_first - timedelta(days=1)


class RecurringTask(db.Model):
    """
    Tarefa que se repete:
      - "weekly": em dias da semana escolhidos (ex.: academia seg/qua/sex);
      - "monthly": uma vez por mês, em qualquer dia (ex.: limpar janelas).
    """
    id = db.Column(db.Integer, primary_key=True)
    user_id = user_fk()
    title = db.Column(db.String(120), nullable=False)
    difficulty = db.Column(db.String(10), nullable=False, default="media")
    frequency = db.Column(db.String(10), nullable=False, default="weekly")
    weekdays = db.Column(db.String(20), nullable=False, default="")  # ex.: "0,2,4"; vazio se mensal
    penalize = db.Column(db.Boolean, nullable=False, default=False)
    created_at = db.Column(db.DateTime, nullable=False, default=datetime.now)
    completions = db.relationship(
        "RecurringCompletion", backref="task", cascade="all, delete-orphan", lazy="dynamic"
    )

    @property
    def is_monthly(self):
        return self.frequency == "monthly"

    @property
    def weekday_list(self):
        return [int(d) for d in self.weekdays.split(",") if d != ""]

    def days_label(self):
        if self.is_monthly:
            return "1x por mês"
        days = self.weekday_list
        if len(days) == 7:
            return "Todo dia"
        if days == [0, 1, 2, 3, 4]:
            return "Dias úteis"
        if days == [5, 6]:
            return "Fim de semana"
        return ", ".join(WEEKDAY_LABELS[d] for d in days)

    def is_scheduled(self, day):
        """Programada para um dia específico (só as semanais têm dias)."""
        return not self.is_monthly and day.weekday() in self.weekday_list

    def done_in_month(self, day):
        """Conclusão no mês de `day`, ou None."""
        first, last = month_bounds(day)
        return self.completions.filter(RecurringCompletion.date.between(first, last)).first()

    def is_done_on(self, day):
        return self.completions.filter_by(date=day).first() is not None

    def reward(self):
        return DIFFICULTY_REWARDS.get(self.difficulty, 10)

    def penalty(self):
        return penalty_for(self.difficulty)


class RecurringCompletion(db.Model):
    """Cada vez que um hábito foi feito (uma linha por dia)."""
    id = db.Column(db.Integer, primary_key=True)
    recurring_id = db.Column(db.Integer, db.ForeignKey("recurring_task.id"), nullable=False)
    date = db.Column(db.Date, nullable=False)
    reward = db.Column(db.Integer, nullable=False)
    __table_args__ = (db.UniqueConstraint("recurring_id", "date"),)
