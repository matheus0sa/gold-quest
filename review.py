"""
Revisão diária.

Na primeira visita do dia, o usuário confirma o que fez nos dias anteriores que
ainda não revisou (no máximo REVIEW_MAX_DAYS para trás). Entram:
  - recorrentes programadas para aquele dia e não marcadas;
  - tarefas únicas cujo prazo caiu naquele dia e continuam pendentes.
"Fiz" paga o gold normal (no prazo). "Não fiz" cobra a penalidade, se a tarefa tiver.
"""
from dataclasses import dataclass
from datetime import date, datetime, timedelta

from models import REVIEW_MAX_DAYS, RecurringCompletion, RecurringTask, Task, db


@dataclass
class ReviewItem:
    key: str            # nome do campo no formulário
    day: date
    task: Task | RecurringTask
    reward: int         # gold se "fiz"
    penalty: int        # gold perdido se "não fiz" (0 = sem penalidade)

    @property
    def recurring(self):
        return isinstance(self.task, RecurringTask)


def review_window(user, today):
    """Dias a revisar: do dia seguinte à última revisão até ontem."""
    end = today - timedelta(days=1)
    start = today - timedelta(days=REVIEW_MAX_DAYS)
    if user.last_review_date is None:
        start = end  # conta anterior à revisão: começa só por ontem
    else:
        start = max(start, user.last_review_date + timedelta(days=1))
    return start, end


def needs_review(user, today):
    start, end = review_window(user, today)
    return start <= end


def pending_items(user, today):
    start, end = review_window(user, today)
    if start > end:
        return []

    items = []
    recurring = RecurringTask.query.filter_by(user_id=user.id).all()
    done = {
        (c.recurring_id, c.date)
        for c in RecurringCompletion.query.join(RecurringTask).filter(
            RecurringTask.user_id == user.id,
            RecurringCompletion.date.between(start, end),
        )
    }
    day = start
    while day <= end:
        for rec in recurring:
            if rec.created_at.date() <= day and rec.is_scheduled(day) and (rec.id, day) not in done:
                items.append(ReviewItem(
                    key=f"rec-{rec.id}-{day.isoformat()}",
                    day=day,
                    task=rec,
                    reward=rec.reward(),
                    penalty=rec.penalty() if rec.penalize else 0,
                ))
        day += timedelta(days=1)

    overdue = Task.query.filter(
        Task.user_id == user.id,
        Task.done.is_(False),
        Task.penalized_at.is_(None),
        Task.due_date.between(start, end),
    )
    for task in overdue:
        items.append(ReviewItem(
            key=f"task-{task.id}",
            day=task.due_date,
            task=task,
            reward=task.calculate_reward(task.due_date),
            penalty=task.penalty() if task.penalize else 0,
        ))

    items.sort(key=lambda i: (i.day, i.task.title.lower()))
    return items


def mark_reviewed(user, today):
    user.last_review_date = today - timedelta(days=1)


def apply_review(user, answers, today):
    """
    answers: {key: "fiz" | "nao"}. Sem resposta conta como "não fiz".
    Devolve (gold ganho, gold perdido). Quem chama faz o commit.
    """
    earned = lost = 0
    for item in pending_items(user, today):
        task = item.task
        label = item.day.strftime("%d/%m")
        if answers.get(item.key) == "fiz":
            if item.recurring:
                db.session.add(RecurringCompletion(task=task, date=item.day, reward=item.reward))
                user.add_gold(item.reward, f"Recorrente concluída ({label}): {task.title}")
            else:
                task.done = True
                task.completed_at = datetime.now()
                task.reward_earned = item.reward
                user.add_gold(item.reward, f"Tarefa concluída: {task.title}")
            earned += item.reward
        elif item.penalty:
            user.add_gold(-item.penalty, f"Penalidade ({label}): {task.title}")
            if not item.recurring:
                task.penalized_at = today
            lost += item.penalty
    mark_reviewed(user, today)
    return earned, lost
