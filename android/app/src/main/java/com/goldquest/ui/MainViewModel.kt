package com.goldquest.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goldquest.GoldQuestApp
import com.goldquest.data.InventoryItem
import com.goldquest.data.RecurringCompletion
import com.goldquest.data.RecurringTask
import com.goldquest.data.ShopItem
import com.goldquest.data.Tag
import com.goldquest.data.Task
import com.goldquest.data.WalletTransaction
import com.goldquest.domain.Msg
import com.goldquest.domain.MsgKind
import com.goldquest.domain.PriceInfo
import com.goldquest.domain.ReviewItem
import com.goldquest.domain.TaskDraft
import com.goldquest.domain.WEEKDAY_LABELS
import com.goldquest.domain.currentPrice
import com.goldquest.domain.isMonthly
import com.goldquest.domain.isScheduled
import com.goldquest.domain.monthBounds
import com.goldquest.domain.reward
import com.goldquest.domain.weekdayIndex
import com.goldquest.domain.weekdayList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Filtro de tag das telas de tarefas: todas, sem tag ou uma tag específica. */
sealed interface TagFilter {
    data object All : TagFilter
    data object None : TagFilter
    data class ById(val id: Long) : TagFilter
}

/** Hábito com a data em que foi feito (hoje, ou no mês para mensais); null = pendente. */
data class HabitRow(val task: RecurringTask, val doneOn: LocalDate?)

data class TasksUi(
    val today: LocalDate,
    val tags: List<Tag>,
    val filter: TagFilter,
    val pending: List<Task>,
    val done: List<Task>,
    val todayHabits: List<HabitRow>,
    val monthly: List<HabitRow>,
    val recurring: List<RecurringTask>,
) {
    val tagsById: Map<Long, Tag> get() = tags.associateBy { it.id }
}

data class DayLoad(val index: Int, val label: String, val count: Int, val gold: Int)

data class WeekUi(
    val tags: List<Tag>,
    val filter: TagFilter,
    val weekly: List<RecurringTask>,
    val monthly: List<RecurringTask>,
    val days: List<DayLoad>,
    val todayIndex: Int,
) {
    val maxCount: Int get() = days.maxOfOrNull { it.count } ?: 0
}

data class Offer(val item: ShopItem, val info: PriceInfo)

data class InventoryUi(val available: List<InventoryItem>, val used: List<InventoryItem>)

data class HistoryUi(val transactions: List<WalletTransaction>, val earned: Int, val spent: Int)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as GoldQuestApp).repository

    private val today = MutableStateFlow(LocalDate.now())
    private val tagFilter = MutableStateFlow<TagFilter>(TagFilter.All)

    private val _messages = MutableSharedFlow<Msg>(extraBufferCapacity = 8)
    val messages: SharedFlow<Msg> = _messages.asSharedFlow()

    /** Itens da revisão do dia; vazio = nada a revisar. Só vale depois de [ready]. */
    private val _review = MutableStateFlow<List<ReviewItem>>(emptyList())
    val review: StateFlow<List<ReviewItem>> = _review

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    val gold: StateFlow<Int> = repo.gold.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val tags: StateFlow<List<Tag>> = repo.tags.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val currentFilter: StateFlow<TagFilter> = combine(tagFilter, repo.tags) { filter, tags ->
        // tag removida: volta para "Todas"
        if (filter is TagFilter.ById && tags.none { it.id == filter.id }) TagFilter.All else filter
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TagFilter.All)

    private val completions = today.flatMapLatest { repo.completionsSince(monthBounds(it).first) }

    private class RawTasks(
        val pending: List<Task>,
        val done: List<Task>,
        val recurring: List<RecurringTask>,
        val completions: List<RecurringCompletion>,
        val tags: List<Tag>,
    )

    val tasksUi: StateFlow<TasksUi?> = combine(
        repo.pendingTasks, repo.doneTasks, repo.recurring, completions, repo.tags,
    ) { pending, done, recurring, comps, tags -> RawTasks(pending, done, recurring, comps, tags) }
        .combine(currentFilter) { raw, filter -> raw to filter }
        .combine(today) { (raw, filter), today -> buildTasksUi(raw, filter, today) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun buildTasksUi(raw: RawTasks, filter: TagFilter, today: LocalDate): TasksUi {
        fun matches(tagId: Long?) = when (filter) {
            TagFilter.All -> true
            TagFilter.None -> tagId == null
            is TagFilter.ById -> tagId == filter.id
        }
        val recurring = raw.recurring.filter { matches(it.tagId) }
        val doneToday = raw.completions.filter { it.date == today }.associateBy { it.recurringId }
        val doneInMonth = raw.completions.associateBy { it.recurringId }  // completionsSince = só este mês
        return TasksUi(
            today = today,
            tags = raw.tags,
            filter = filter,
            pending = raw.pending.filter { matches(it.tagId) },
            done = raw.done.filter { matches(it.tagId) }.take(10),
            todayHabits = recurring.filter { it.isScheduled(today) }
                .map { HabitRow(it, doneToday[it.id]?.date) },
            // pendentes primeiro
            monthly = recurring.filter { it.isMonthly }
                .map { HabitRow(it, doneInMonth[it.id]?.date) }
                .sortedBy { it.doneOn != null },
            recurring = recurring,
        )
    }

    val weekUi: StateFlow<WeekUi?> = combine(repo.recurring, repo.tags, currentFilter, today) { all, tags, filter, today ->
        val filtered = all.filter {
            when (filter) {
                TagFilter.All -> true
                TagFilter.None -> it.tagId == null
                is TagFilter.ById -> it.tagId == filter.id
            }
        }.sortedBy { it.title.lowercase() }
        val weekly = filtered.filter { !it.isMonthly }
        WeekUi(
            tags = tags,
            filter = filter,
            weekly = weekly,
            monthly = filtered.filter { it.isMonthly },
            days = WEEKDAY_LABELS.mapIndexed { i, label ->
                val ofDay = weekly.filter { i in it.weekdayList() }
                DayLoad(i, label, ofDay.size, ofDay.sumOf { it.reward() })
            },
            todayIndex = today.weekdayIndex,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val offers: StateFlow<List<Offer>> = combine(repo.shopItems, repo.inventory, today) { items, inventory, today ->
        val byItem = inventory.groupBy { it.shopItemId }
        items.map { Offer(it, currentPrice(it, byItem[it.id].orEmpty(), today)) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val inventoryUi: StateFlow<InventoryUi> = repo.inventory.map { all ->
        InventoryUi(
            available = all.filter { it.usedAt == null }.sortedBy { it.purchasedAt },
            used = all.filter { it.usedAt != null }.sortedByDescending { it.usedAt }.take(10),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, InventoryUi(emptyList(), emptyList()))

    val historyUi: StateFlow<HistoryUi> = combine(repo.transactions, repo.totalEarned, repo.totalSpent) { t, e, s ->
        HistoryUi(t, e, s)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HistoryUi(emptyList(), 0, 0))

    init {
        viewModelScope.launch {
            repo.seedIfNeeded(today.value)
            _review.value = repo.reviewItems(today.value)
            _ready.value = true
        }
    }

    /** Chamado ao voltar para o app: o dia pode ter virado enquanto ele estava em segundo plano. */
    fun refreshDay() {
        if (!_ready.value) return
        val now = LocalDate.now()
        today.value = now
        viewModelScope.launch { _review.value = repo.reviewItems(now) }
    }

    fun todayDate(): LocalDate = today.value

    private fun run(block: suspend () -> Msg?) {
        viewModelScope.launch { block()?.let { _messages.emit(it) } }
    }

    fun setFilter(filter: TagFilter) { tagFilter.value = filter }

    fun addTask(draft: TaskDraft) = run { repo.addTask(draft) }
    fun updateTask(id: Long, draft: TaskDraft, onDone: () -> Unit) = viewModelScope.launch {
        val msg = repo.updateTask(id, draft, today.value)
        _messages.emit(msg)
        if (msg.kind != MsgKind.ERROR) onDone()
    }
    fun completeTask(id: Long) = run { repo.completeTask(id, today.value) }
    fun deleteTask(id: Long) = run { repo.deleteTask(id) }

    fun completeRecurring(id: Long) = run { repo.completeRecurring(id, today.value) }
    fun updateRecurring(id: Long, draft: TaskDraft, onDone: () -> Unit) = viewModelScope.launch {
        val msg = repo.updateRecurring(id, draft)
        _messages.emit(msg)
        if (msg.kind != MsgKind.ERROR) onDone()
    }
    fun deleteRecurring(id: Long) = run { repo.deleteRecurring(id) }

    suspend fun addTag(name: String): Msg = repo.addTag(name)
    fun deleteTag(id: Long) = run { repo.deleteTag(id); null }

    fun addShopItem(name: String, emoji: String, price: Int?) = run { repo.addShopItem(name, emoji, price) }
    fun buyItem(id: Long) = run { repo.buyItem(id, today.value) }
    fun deleteShopItem(id: Long) = run { repo.deleteShopItem(id) }
    fun useItem(id: Long) = run { repo.useItem(id) }

    fun submitReview(answers: Map<String, Boolean>) = run {
        val msg = repo.applyReview(answers, today.value)
        _review.value = emptyList()
        msg
    }

    suspend fun loadTask(id: Long) = repo.task(id)
    suspend fun loadRecurring(id: Long) = repo.recurringTask(id)
}
