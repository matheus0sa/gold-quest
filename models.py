from datetime import datetime
from flask_sqlalchemy import SQLAlchemy

db = SQLAlchemy()

# Quanto cada dificuldade paga de gold
DIFFICULTY_REWARDS = {"facil": 10, "media": 30, "dificil": 60}
DIFFICULTY_LABELS = {"facil": "Fácil", "media": "Média", "dificil": "Difícil"}

ON_TIME_BONUS = 1.2   # +20% se concluir até o prazo
LATE_PENALTY = 0.5    # metade do gold se concluir atrasado


class Wallet(db.Model):
    """Carteira única (app de um usuário só, por enquanto)."""
    id = db.Column(db.Integer, primary_key=True)
    gold = db.Column(db.Integer, nullable=False, default=0)


class Task(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    title = db.Column(db.String(120), nullable=False)
    difficulty = db.Column(db.String(10), nullable=False, default="media")
    due_date = db.Column(db.Date, nullable=True)
    done = db.Column(db.Boolean, nullable=False, default=False)
    created_at = db.Column(db.DateTime, nullable=False, default=datetime.now)
    completed_at = db.Column(db.DateTime, nullable=True)
    reward_earned = db.Column(db.Integer, nullable=True)

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
    name = db.Column(db.String(80), nullable=False)
    price = db.Column(db.Integer, nullable=False)  # preço base
    emoji = db.Column(db.String(8), nullable=False, default="🎁")
    created_at = db.Column(db.DateTime, nullable=True, default=datetime.now)


class InventoryItem(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    shop_item_id = db.Column(db.Integer, db.ForeignKey("shop_item.id"), nullable=True)
    price_paid = db.Column(db.Integer, nullable=True)
    name = db.Column(db.String(80), nullable=False)
    emoji = db.Column(db.String(8), nullable=False, default="🎁")
    purchased_at = db.Column(db.DateTime, nullable=False, default=datetime.now)
    used_at = db.Column(db.DateTime, nullable=True)


class Transaction(db.Model):
    """Histórico de tudo que entra (+) e sai (-) da carteira."""
    id = db.Column(db.Integer, primary_key=True)
    amount = db.Column(db.Integer, nullable=False)
    description = db.Column(db.String(160), nullable=False)
    created_at = db.Column(db.DateTime, nullable=False, default=datetime.now)


WEEKDAY_LABELS = ["Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"]  # date.weekday(): 0 = segunda


class RecurringTask(db.Model):
    """Hábito que se repete em dias da semana escolhidos (ex.: academia seg/qua/sex)."""
    id = db.Column(db.Integer, primary_key=True)
    title = db.Column(db.String(120), nullable=False)
    difficulty = db.Column(db.String(10), nullable=False, default="media")
    weekdays = db.Column(db.String(20), nullable=False)  # ex.: "0,2,4"
    created_at = db.Column(db.DateTime, nullable=False, default=datetime.now)
    completions = db.relationship(
        "RecurringCompletion", backref="task", cascade="all, delete-orphan", lazy="dynamic"
    )

    @property
    def weekday_list(self):
        return [int(d) for d in self.weekdays.split(",") if d != ""]

    def days_label(self):
        days = self.weekday_list
        if len(days) == 7:
            return "Todo dia"
        if days == [0, 1, 2, 3, 4]:
            return "Dias úteis"
        if days == [5, 6]:
            return "Fim de semana"
        return ", ".join(WEEKDAY_LABELS[d] for d in days)

    def is_scheduled(self, day):
        return day.weekday() in self.weekday_list

    def is_done_on(self, day):
        return self.completions.filter_by(date=day).first() is not None

    def reward(self):
        return DIFFICULTY_REWARDS.get(self.difficulty, 10)


class RecurringCompletion(db.Model):
    """Cada vez que um hábito foi feito (uma linha por dia)."""
    id = db.Column(db.Integer, primary_key=True)
    recurring_id = db.Column(db.Integer, db.ForeignKey("recurring_task.id"), nullable=False)
    date = db.Column(db.Date, nullable=False)
    reward = db.Column(db.Integer, nullable=False)
    __table_args__ = (db.UniqueConstraint("recurring_id", "date"),)
