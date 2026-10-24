package com.goldquest.domain

import androidx.room.withTransaction
import com.goldquest.data.AppDatabase
import com.goldquest.data.Difficulty
import com.goldquest.data.Frequency
import com.goldquest.data.InventoryItem
import com.goldquest.data.RecurringCompletion
import com.goldquest.data.RecurringTask
import com.goldquest.data.Settings
import com.goldquest.data.ShopItem
import com.goldquest.data.Tag
import com.goldquest.data.Task
import com.goldquest.data.WalletTransaction
import java.time.LocalDate
import java.time.LocalDateTime

enum class MsgKind { OK, GOLD, ERROR }

/** Mensagem curta para o usuário (vira um aviso na tela). */
data class Msg(val text: String, val kind: MsgKind)

private fun ok(text: String) = Msg(text, MsgKind.OK)
private fun gold(text: String) = Msg(text, MsgKind.GOLD)
private fun error(text: String) = Msg(text, MsgKind.ERROR)

enum class Repeat { NONE, WEEKLY, MONTHLY }

enum class DraftKind {
    NEW,        // o formulário decide se é recorrente
    TASK,       // edição de tarefa única
    RECURRING,  // edição de tarefa recorrente
}

/** Dados do formulário de tarefa, usado para criar e editar. */
data class TaskDraft(
    val title: String = "",
    val difficulty: Difficulty = Difficulty.MEDIA,
    val penalize: Boolean = false,
    val tagId: Long? = null,
    val repeat: Repeat = Repeat.NONE,
    val weekdays: Set<Int> = emptySet(),
    val dueDate: LocalDate? = null,
) {
    /** Mensagem de erro, ou null se o formulário está válido. */
    fun validate(kind: DraftKind): String? {
        if (title.isBlank()) return "Escreva um título para a tarefa."
        val recurring = if (kind == DraftKind.NEW) repeat != Repeat.NONE else kind == DraftKind.RECURRING
        if (recurring) {
            if (repeat == Repeat.NONE || (repeat == Repeat.WEEKLY && weekdays.isEmpty())) {
                return "Escolha os dias da semana ou a opção mensal."
            }
            return null
        }
        if (penalize && dueDate == null) {
            return "Para ter penalidade, a tarefa precisa de um prazo ou de repetição."
        }
        return null
    }
}

/** O que ainda falta fazer hoje, para o lembrete diário. */
data class ReminderSummary(val habits: Int, val monthly: Int, val tasksDue: Int) {
    val total: Int get() = habits + monthly + tasksDue
}

/** Mensais só entram no lembrete na última semana do mês, para não cobrar o mês inteiro. */
private const val MONTHLY_REMINDER_DAYS = 7

class GoldRepository(private val db: AppDatabase) {
    private val dao = db.dao()

    val tags = dao.tags()
    val gold = dao.gold()
    val pendingTasks = dao.pendingTasks()
    val doneTasks = dao.doneTasks()
    val recurring = dao.recurring()
    val shopItems = dao.shopItems()
    val inventory = dao.inventory()
    val transactions = dao.recentTransactions()
    val totalEarned = dao.totalEarned()
    val totalSpent = dao.totalSpent()

    fun completionsSince(since: LocalDate) = dao.completionsSince(since)

    suspend fun task(id: Long) = dao.task(id)
    suspend fun recurringTask(id: Long) = dao.recurring(id)

    /** Primeiro uso: loja e tags padrão, e a revisão começa a valer a partir de hoje. */
    suspend fun seedIfNeeded(today: LocalDate) = db.withTransaction {
        if (dao.settings() != null) return@withTransaction
        dao.save(Settings(lastReviewDate = today.minusDays(1)))
        dao.insert(ShopItem(name = "1 hora de videogame", price = 100, emoji = "🎮"))
        dao.insert(ShopItem(name = "Episódio de série", price = 50, emoji = "📺"))
        dao.insert(ShopItem(name = "Pedir delivery", price = 300, emoji = "🍕"))
        DEFAULT_TAGS.forEach { dao.insert(Tag(name = it)) }
    }

    private suspend fun addGold(amount: Int, description: String) {
        dao.insert(WalletTransaction(amount = amount, description = description))
    }

    // ---------- Tarefas ----------

    suspend fun addTask(draft: TaskDraft): Msg {
        draft.validate(DraftKind.NEW)?.let { return error(it) }
        val title = draft.title.trim().take(120)
        if (draft.repeat == Repeat.NONE) {
            dao.insert(
                Task(
                    title = title, difficulty = draft.difficulty, penalize = draft.penalize,
                    tagId = draft.tagId, dueDate = draft.dueDate,
                )
            )
            return ok("Tarefa adicionada.")
        }
        dao.insert(draft.toRecurring(title))
        return ok("Tarefa recorrente adicionada.")
    }

    private fun TaskDraft.toRecurring(title: String, id: Long = 0, createdAt: LocalDateTime = LocalDateTime.now()) =
        RecurringTask(
            id = id, title = title, difficulty = difficulty, penalize = penalize, tagId = tagId,
            frequency = if (repeat == Repeat.MONTHLY) Frequency.MONTHLY else Frequency.WEEKLY,
            weekdays = if (repeat == Repeat.MONTHLY) "" else weekdays.sorted().joinToString(","),
            createdAt = createdAt,
        )

    suspend fun updateTask(id: Long, draft: TaskDraft, today: LocalDate): Msg {
        val task = dao.task(id) ?: return error("Tarefa não encontrada.")
        if (task.done) return error("Tarefas concluídas não podem ser editadas.")
        draft.validate(DraftKind.TASK)?.let { return error(it) }
        val newDue = draft.dueDate
        if (newDue != task.dueDate && newDue != null && newDue < today) {
            return error("O novo prazo não pode estar no passado.")
        }
        dao.update(
            task.copy(
                title = draft.title.trim().take(120), difficulty = draft.difficulty,
                penalize = draft.penalize, tagId = draft.tagId, dueDate = newDue,
                // prazo novo, penalidade volta a valer
                penalizedAt = if (newDue != task.dueDate) null else task.penalizedAt,
            )
        )
        return ok("Tarefa atualizada.")
    }

    suspend fun completeTask(id: Long, today: LocalDate): Msg? = db.withTransaction {
        val task = dao.task(id) ?: return@withTransaction null
        if (task.done) return@withTransaction null
        val reward = task.calculateReward(today)
        dao.update(task.copy(done = true, completedAt = LocalDateTime.now(), rewardEarned = reward))
        addGold(reward, "Tarefa concluída: ${task.title}")
        gold("+$reward gold por concluir “${task.title}”")
    }

    suspend fun deleteTask(id: Long): Msg {
        dao.deleteTask(id)
        return ok("Tarefa excluída.")
    }

    // ---------- Recorrentes ----------

    suspend fun completeRecurring(id: Long, today: LocalDate): Msg? = db.withTransaction {
        val rec = dao.recurring(id) ?: return@withTransaction null
        if (rec.isMonthly) {
            val (first, last) = monthBounds(today)
            if (dao.completionsBetween(first, last).any { it.recurringId == id }) return@withTransaction null
        } else if (!rec.isScheduled(today)) {
            return@withTransaction error("“${rec.title}” não está programada para hoje.")
        } else if (dao.completionsBetween(today, today).any { it.recurringId == id }) {
            return@withTransaction null
        }
        val reward = rec.reward()
        dao.insert(RecurringCompletion(recurringId = id, date = today, reward = reward))
        addGold(reward, "Recorrente concluída: ${rec.title}")
        gold("+$reward gold por “${rec.title}”")
    }

    suspend fun updateRecurring(id: Long, draft: TaskDraft): Msg {
        val rec = dao.recurring(id) ?: return error("Tarefa não encontrada.")
        draft.validate(DraftKind.RECURRING)?.let { return error(it) }
        // Conclusões antigas guardam o gold que pagaram; a mudança vale daqui em diante
        dao.update(draft.toRecurring(draft.title.trim().take(120), id = id, createdAt = rec.createdAt))
        return ok("Tarefa recorrente atualizada.")
    }

    suspend fun deleteRecurring(id: Long): Msg {
        dao.deleteRecurring(id)
        return ok("Tarefa recorrente excluída.")
    }

    // ---------- Tags ----------

    suspend fun addTag(rawName: String): Msg {
        val name = rawName.trim().split(Regex("\\s+")).joinToString(" ").take(MAX_TAG_NAME)
        if (name.isEmpty()) return error("Dê um nome para a tag.")
        val tags = dao.tagsOnce()
        if (tags.any { it.name.equals(name, ignoreCase = true) }) return error("A tag “$name” já existe.")
        if (tags.size >= MAX_TAGS) return error("Limite de $MAX_TAGS tags atingido.")
        dao.insert(Tag(name = name))
        return ok("Tag “$name” criada.")
    }

    /** As tarefas continuam existindo, só ficam sem tag (chave estrangeira SET NULL). */
    suspend fun deleteTag(id: Long) = dao.deleteTag(id)

    // ---------- Loja ----------

    suspend fun addShopItem(rawName: String, rawEmoji: String, price: Int?): Msg {
        val name = rawName.trim()
        if (name.isEmpty() || price == null || price <= 0) {
            return error("Dê um nome e um preço maior que zero para a recompensa.")
        }
        val emoji = rawEmoji.trim().ifEmpty { "🎁" }.take(8)
        dao.insert(ShopItem(name = name.take(80), price = price, emoji = emoji))
        return ok("$name agora está na loja.")
    }

    suspend fun buyItem(id: Long, today: LocalDate): Msg = db.withTransaction {
        val item = dao.shopItem(id) ?: return@withTransaction error("Item não encontrado.")
        val price = currentPrice(item, dao.purchasesOf(id), today).price  // sempre recalcula na hora
        val balance = dao.goldOnce()
        if (balance < price) return@withTransaction error("Faltam ${price - balance} gold para comprar ${item.name}.")
        addGold(-price, "Compra: ${item.name}")
        dao.insert(InventoryItem(shopItemId = id, pricePaid = price, name = item.name, emoji = item.emoji))
        ok("${item.name} foi para o seu inventário por $price gold.")
    }

    /** Mantém o inventário: a chave estrangeira só desfaz a ligação com o item removido. */
    suspend fun deleteShopItem(id: Long): Msg {
        dao.deleteShopItem(id)
        return ok("Recompensa removida da loja.")
    }

    suspend fun useItem(id: Long): Msg? {
        val inv = dao.inventoryItem(id) ?: return null
        if (inv.usedAt != null) return null
        dao.update(inv.copy(usedAt = LocalDateTime.now()))
        return ok("Aproveite: ${inv.name}!")
    }

    // ---------- Lembrete ----------

    suspend fun reminderSummary(today: LocalDate): ReminderSummary {
        val (first, last) = monthBounds(today)
        val recurring = dao.recurringOnce()
        val completions = dao.completionsBetween(first, today)
        val habits = recurring.count { rec ->
            rec.isScheduled(today) && completions.none { it.recurringId == rec.id && it.date == today }
        }
        val closingMonth = last.dayOfMonth - today.dayOfMonth < MONTHLY_REMINDER_DAYS
        val monthly = if (!closingMonth) 0 else recurring.count { rec ->
            rec.isMonthly && completions.none { it.recurringId == rec.id }
        }
        return ReminderSummary(habits, monthly, dao.dueTasksUntil(today).size)
    }

    // ---------- Revisão diária ----------

    private suspend fun lastReview(): LocalDate? = dao.settings()?.lastReviewDate

    private suspend fun markReviewed(today: LocalDate) =
        dao.save(Settings(lastReviewDate = today.minusDays(1)))

    private suspend fun pendingItems(today: LocalDate): List<ReviewItem> {
        val (start, end) = reviewWindow(lastReview(), today)
        if (start > end) return emptyList()
        return buildReviewItems(
            start = start,
            end = end,
            recurring = dao.recurringOnce(),
            completions = dao.completionsBetween(completionsFrom(start), end),
            overdue = dao.overdueTasks(start, end),
        )
    }

    /**
     * Itens da revisão de hoje. Se a janela existe mas não sobrou nada a perguntar,
     * já marca o dia como revisado e devolve lista vazia.
     */
    suspend fun reviewItems(today: LocalDate): List<ReviewItem> = db.withTransaction {
        if (!needsReview(lastReview(), today)) return@withTransaction emptyList()
        val items = pendingItems(today)
        if (items.isEmpty()) markReviewed(today)
        items
    }

    /** answers: key -> true ("fiz") ou false ("não fiz"). Sem resposta conta como "não fiz". */
    suspend fun applyReview(answers: Map<String, Boolean>, today: LocalDate): Msg = db.withTransaction {
        var earned = 0
        var lost = 0
        for (item in pendingItems(today)) {
            val label = if (item.monthly) MONTH_LABELS[item.day.monthValue - 1] else formatDay(item.day)
            if (answers[item.key] == true) {
                if (item.recurring) {
                    dao.insert(RecurringCompletion(recurringId = item.taskId, date = item.day, reward = item.reward))
                    addGold(item.reward, "Recorrente concluída ($label): ${item.title}")
                } else {
                    val task = dao.task(item.taskId) ?: continue
                    dao.update(task.copy(done = true, completedAt = LocalDateTime.now(), rewardEarned = item.reward))
                    addGold(item.reward, "Tarefa concluída: ${item.title}")
                }
                earned += item.reward
            } else if (item.penalty > 0) {
                addGold(-item.penalty, "Penalidade ($label): ${item.title}")
                if (!item.recurring) {
                    dao.task(item.taskId)?.let { dao.update(it.copy(penalizedAt = today)) }
                }
                lost += item.penalty
            }
        }
        markReviewed(today)
        val parts = buildList {
            if (earned > 0) add("+$earned gold recuperado")
            if (lost > 0) add("−$lost gold de penalidade")
        }
        val text = "Revisão concluída" + if (parts.isEmpty()) "." else ": ${parts.joinToString(", ")}."
        Msg(text, if (earned > 0) MsgKind.GOLD else MsgKind.OK)
    }

    private companion object {
        const val MAX_TAG_NAME = 30
    }
}
