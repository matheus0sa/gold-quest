package com.goldquest.domain

import com.goldquest.data.Difficulty
import com.goldquest.data.Frequency
import com.goldquest.data.InventoryItem
import com.goldquest.data.RecurringTask
import com.goldquest.data.ShopItem
import com.goldquest.data.Task
import java.time.LocalDate
import kotlin.math.max

const val ON_TIME_BONUS = 1.2    // +20% se concluir até o prazo
const val LATE_PENALTY = 0.5     // metade do gold se concluir atrasado
const val MISSED_PENALTY = 0.10  // tarefa com penalidade não feita: perde 10% do valor base
const val REVIEW_MAX_DAYS = 7    // a revisão diária olha no máximo uma semana para trás

val DEFAULT_TAGS = listOf("estudo", "faxina", "fitness", "trabalho")  // o app novo começa com estas
const val MAX_TAGS = 30

val WEEKDAY_LABELS = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")  // 0 = segunda
val MONTH_LABELS = listOf(
    "janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho",
    "agosto", "setembro", "outubro", "novembro", "dezembro",
)

/** Arredonda como o `round` do Python (metade vai para o par), para manter os mesmos valores. */
fun roundHalfEven(value: Double): Int = Math.rint(value).toInt()

fun penaltyFor(difficulty: Difficulty): Int =
    max(1, roundHalfEven(difficulty.reward * MISSED_PENALTY))

/** 0 = segunda ... 6 = domingo. */
val LocalDate.weekdayIndex: Int get() = dayOfWeek.value - 1

fun monthBounds(day: LocalDate): Pair<LocalDate, LocalDate> {
    val first = day.withDayOfMonth(1)
    return first to first.plusMonths(1).minusDays(1)
}

// ---------- Tarefa única ----------

enum class DueStatus { LATE, TODAY, FUTURE }

fun Task.penalty(): Int = penaltyFor(difficulty)

fun Task.calculateReward(today: LocalDate): Int {
    val base = difficulty.reward
    val due = dueDate ?: return base
    return if (today <= due) roundHalfEven(base * ON_TIME_BONUS)
    else max(1, roundHalfEven(base * LATE_PENALTY))
}

fun Task.gemReward(): GemReward? = gemRewardOf(gem, gemAmount)

fun Task.dueStatus(today: LocalDate): DueStatus? {
    val due = dueDate ?: return null
    return when {
        today > due -> DueStatus.LATE
        today == due -> DueStatus.TODAY
        else -> DueStatus.FUTURE
    }
}

// ---------- Tarefa recorrente ----------

val RecurringTask.isMonthly: Boolean get() = frequency == Frequency.MONTHLY

/** "0,2,4" -> [0, 2, 4], ordenado e sem repetição. */
fun parseWeekdays(raw: String): List<Int> =
    raw.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 0..6 }.distinct().sorted()

fun formatWeekdays(days: Collection<Int>): String = days.filter { it in 0..6 }.distinct().sorted().joinToString(",")

/** Nome curto para um conjunto de dias; vazio = [empty]. */
fun weekdaysLabel(days: List<Int>, empty: String): String = when {
    days.isEmpty() -> empty
    days.size == 7 -> "Todo dia"
    days == listOf(0, 1, 2, 3, 4) -> "Dias úteis"
    days == listOf(5, 6) -> "Fim de semana"
    else -> days.joinToString(", ") { WEEKDAY_LABELS[it] }
}

fun RecurringTask.weekdayList(): List<Int> = parseWeekdays(weekdays)

fun RecurringTask.daysLabel(): String =
    if (isMonthly) "1x por mês" else weekdaysLabel(weekdayList(), "Nenhum dia")

/** Programada para um dia específico (só as semanais têm dias). */
fun RecurringTask.isScheduled(day: LocalDate): Boolean =
    !isMonthly && day.weekdayIndex in weekdayList()

fun RecurringTask.reward(): Int = difficulty.reward

fun RecurringTask.penalty(): Int = penaltyFor(difficulty)

fun RecurringTask.gemReward(): GemReward? = gemRewardOf(gem, gemAmount)

// ---------- Recompensas por dia da semana ----------

fun ShopItem.weekdayList(): List<Int> = parseWeekdays(weekdays)

fun ShopItem.daysLabel(): String = weekdaysLabel(weekdayList(), "Qualquer dia")

fun InventoryItem.weekdayList(): List<Int> = parseWeekdays(weekdays)

fun InventoryItem.daysLabel(): String = weekdaysLabel(weekdayList(), "Qualquer dia")

/** Sem dias escolhidos, vale qualquer dia. */
private fun allowedOn(days: List<Int>, day: LocalDate) = days.isEmpty() || day.weekdayIndex in days

fun InventoryItem.usableOn(day: LocalDate): Boolean = allowedOn(weekdayList(), day)

/** Item com dias só pode ser comprado nos dias em que pode ser usado. */
fun ShopItem.availableOn(day: LocalDate): Boolean = allowedOn(weekdayList(), day)
