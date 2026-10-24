package com.goldquest.domain

import com.goldquest.data.Difficulty
import com.goldquest.data.RecurringCompletion
import com.goldquest.data.RecurringTask
import com.goldquest.data.Task
import java.time.LocalDate

/*
 * Revisão diária.
 *
 * Na primeira visita do dia, o usuário confirma o que fez nos dias anteriores que
 * ainda não revisou (no máximo REVIEW_MAX_DAYS para trás). Entram:
 *  - recorrentes programadas para aquele dia e não marcadas;
 *  - mensais não feitas num mês que terminou (no mês de criação, sem penalidade);
 *  - tarefas únicas cujo prazo caiu naquele dia e continuam pendentes.
 * "Fiz" paga o gold normal (no prazo). "Não fiz" cobra a penalidade, se a tarefa tiver.
 */

enum class ReviewKind { TASK, WEEKLY, MONTHLY }

data class ReviewItem(
    val key: String,  // identifica a pergunta nas respostas
    val day: LocalDate,
    val kind: ReviewKind,
    val taskId: Long,
    val title: String,
    val difficulty: Difficulty,
    val detail: String,  // "Prazo 12/03", "🔁 Seg, Qua", "📅 Mensal de março"
    val reward: Int,     // gold se "fiz"
    val penalty: Int,    // gold perdido se "não fiz" (0 = sem penalidade)
) {
    val recurring: Boolean get() = kind != ReviewKind.TASK
    val monthly: Boolean get() = kind == ReviewKind.MONTHLY
}

/** Dias a revisar: do dia seguinte à última revisão até ontem (start > end = nada a revisar). */
fun reviewWindow(lastReview: LocalDate?, today: LocalDate): Pair<LocalDate, LocalDate> {
    val end = today.minusDays(1)
    val start = if (lastReview == null) end else maxOf(today.minusDays(REVIEW_MAX_DAYS.toLong()), lastReview.plusDays(1))
    return start to end
}

fun needsReview(lastReview: LocalDate?, today: LocalDate): Boolean {
    val (start, end) = reviewWindow(lastReview, today)
    return start <= end
}

/** Primeiro dia de que as conclusões precisam, para montar os itens da janela. */
fun completionsFrom(start: LocalDate): LocalDate = monthBounds(start).first

fun buildReviewItems(
    start: LocalDate,
    end: LocalDate,
    recurring: List<RecurringTask>,
    completions: List<RecurringCompletion>,  // cobrindo de completionsFrom(start) até end
    overdue: List<Task>,
): List<ReviewItem> {
    if (start > end) return emptyList()

    val items = mutableListOf<ReviewItem>()
    val weekly = recurring.filter { !it.isMonthly }
    val monthly = recurring.filter { it.isMonthly }
    val done = completions.map { it.recurringId to it.date }.toSet()

    var day = start
    while (day <= end) {
        for (rec in weekly) {
            if (rec.createdAt.toLocalDate() <= day && rec.isScheduled(day) && (rec.id to day) !in done) {
                items += ReviewItem(
                    key = "rec-${rec.id}-$day",
                    day = day,
                    kind = ReviewKind.WEEKLY,
                    taskId = rec.id,
                    title = rec.title,
                    difficulty = rec.difficulty,
                    detail = "🔁 ${rec.daysLabel()}",
                    reward = rec.reward(),
                    penalty = if (rec.penalize) rec.penalty() else 0,
                )
            }
        }
        if (day.plusDays(1).dayOfMonth == 1) {  // último dia do mês: fecha as mensais
            val (first, _) = monthBounds(day)
            for (rec in monthly) {
                val created = rec.createdAt.toLocalDate()
                val doneInMonth = completions.any { it.recurringId == rec.id && it.date >= first && it.date <= day }
                if (created <= day && !doneInMonth) {
                    items += ReviewItem(
                        key = "mon-${rec.id}-$day",
                        day = day,
                        kind = ReviewKind.MONTHLY,
                        taskId = rec.id,
                        title = rec.title,
                        difficulty = rec.difficulty,
                        detail = "📅 Mensal de ${MONTH_LABELS[day.monthValue - 1]}",
                        reward = rec.reward(),
                        penalty = if (rec.penalize && created < first) rec.penalty() else 0,
                    )
                }
            }
        }
        day = day.plusDays(1)
    }

    for (task in overdue) {
        val due = task.dueDate ?: continue
        items += ReviewItem(
            key = "task-${task.id}",
            day = due,
            kind = ReviewKind.TASK,
            taskId = task.id,
            title = task.title,
            difficulty = task.difficulty,
            detail = "Prazo ${formatDay(due)}",
            reward = task.calculateReward(due),
            penalty = if (task.penalize) task.penalty() else 0,
        )
    }

    return items.sortedWith(compareBy<ReviewItem> { it.day }.thenBy { it.title.lowercase() })
}
