package com.goldquest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goldquest.data.RecurringTask
import com.goldquest.data.Task
import com.goldquest.domain.DraftKind
import com.goldquest.domain.DueStatus
import com.goldquest.domain.MONTH_LABELS
import com.goldquest.domain.TaskDraft
import com.goldquest.domain.calculateReward
import com.goldquest.domain.daysLabel
import com.goldquest.domain.dueStatus
import com.goldquest.domain.formatDateTime
import com.goldquest.domain.formatDay
import com.goldquest.domain.penalty
import com.goldquest.domain.reward

private class PendingDelete(val message: String, val action: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    ui: TasksUi,
    vm: MainViewModel,
    onEditTask: (Long) -> Unit,
    onEditRecurring: (Long) -> Unit,
) {
    var showForm by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }
    val tagsById = ui.tagsById

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { ScreenTitle("Tarefas") }
            item { TagFilterBar(ui.tags, ui.filter, vm::setFilter) }

            if (ui.todayHabits.isNotEmpty()) {
                item {
                    SectionTitle("Hoje", "${ui.todayHabits.count { it.doneOn != null }}/${ui.todayHabits.size}")
                }
                items(ui.todayHabits, key = { "today-${it.task.id}" }) { row ->
                    val rec = row.task
                    HabitCard(rec, tagsById, row.doneOn != null, vm) {
                        MetaText("🔁 ${rec.daysLabel()}")
                        if (rec.penalize && row.doneOn == null) PenaltyBadge(rec.penalty())
                    }
                }
            }

            if (ui.monthly.isNotEmpty()) {
                item {
                    SectionTitle(
                        "Este mês",
                        "${ui.monthly.count { it.doneOn != null }}/${ui.monthly.size}",
                    )
                }
                items(ui.monthly, key = { "month-${it.task.id}" }) { row ->
                    val rec = row.task
                    HabitCard(rec, tagsById, row.doneOn != null, vm) {
                        if (row.doneOn != null) MetaText("Feito em ${formatDay(row.doneOn)}")
                        else MetaText("📅 Até o fim de ${MONTH_LABELS[ui.today.monthValue - 1]}")
                        if (rec.penalize && row.doneOn == null) PenaltyBadge(rec.penalty())
                    }
                }
            }

            item { SectionTitle("Pendentes", ui.pending.size.toString()) }
            if (ui.pending.isEmpty()) {
                item {
                    EmptyText(
                        if (ui.filter == TagFilter.All) "Nenhuma tarefa pendente. Toque em + para começar a juntar gold."
                        else "Nenhuma tarefa pendente com este filtro."
                    )
                }
            }
            items(ui.pending, key = { "task-${it.id}" }) { task ->
                PendingTaskCard(
                    task = task,
                    ui = ui,
                    onComplete = { vm.completeTask(task.id) },
                    onEdit = { onEditTask(task.id) },
                    onDelete = { pendingDelete = PendingDelete("Excluir esta tarefa?") { vm.deleteTask(task.id) } },
                )
            }

            if (ui.recurring.isNotEmpty()) {
                item { SectionTitle("Recorrentes", ui.recurring.size.toString()) }
                items(ui.recurring, key = { "rec-${it.id}" }) { rec ->
                    TaskCard(
                        title = rec.title,
                        leading = { Text("🔁") },
                        actions = {
                            RowActions(
                                onEdit = { onEditRecurring(rec.id) },
                                onDelete = {
                                    pendingDelete = PendingDelete("Excluir esta tarefa recorrente?") {
                                        vm.deleteRecurring(rec.id)
                                    }
                                },
                            )
                        },
                    ) {
                        DifficultyChip(rec.difficulty)
                        rec.tagId?.let { tagsById[it] }?.let { TagChip(it) }
                        MetaText(rec.daysLabel())
                        if (rec.penalize) PenaltyBadge(rec.penalty())
                    }
                }
            }

            if (ui.done.isNotEmpty()) {
                item { SectionTitle("Concluídas recentemente") }
                items(ui.done, key = { "done-${it.id}" }) { task ->
                    TaskCard(
                        title = task.title,
                        muted = true,
                        leading = { CheckCircle(true, null, "") },
                        trailingText = "+${task.rewardEarned ?: 0}",
                    ) {
                        task.completedAt?.let { MetaText(formatDateTime(it)) }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showForm = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) { Text("+ Nova tarefa", fontWeight = FontWeight.SemiBold) }
    }

    if (showForm) {
        ModalBottomSheet(
            onDismissRequest = { showForm = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                val filter = ui.filter
                TaskForm(
                    kind = DraftKind.NEW,
                    initial = TaskDraft(tagId = (filter as? TagFilter.ById)?.id),
                    tags = ui.tags,
                    submitLabel = "Adicionar tarefa",
                    vm = vm,
                ) { draft ->
                    vm.addTask(draft)
                    showForm = false
                }
                Text(
                    "No prazo: +20% de gold. Atrasada: metade.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    pendingDelete?.let { pd ->
        ConfirmDialog(pd.message, onConfirm = pd.action, onDismiss = { pendingDelete = null })
    }
}

@Composable
private fun HabitCard(
    rec: RecurringTask,
    tagsById: Map<Long, com.goldquest.data.Tag>,
    done: Boolean,
    vm: MainViewModel,
    extraMeta: @Composable () -> Unit,
) {
    TaskCard(
        title = rec.title,
        muted = done,
        leading = { CheckCircle(done, { vm.completeRecurring(rec.id) }, "Concluir ${rec.title}") },
        trailingText = (if (done) "✓ " else "") + "+${rec.reward()}",
    ) {
        DifficultyChip(rec.difficulty)
        rec.tagId?.let { tagsById[it] }?.let { TagChip(it) }
        extraMeta()
    }
}

@Composable
private fun PendingTaskCard(
    task: Task,
    ui: TasksUi,
    onComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    TaskCard(
        title = task.title,
        leading = { CheckCircle(false, onComplete, "Concluir ${task.title}") },
        trailingText = "+${task.calculateReward(ui.today)}",
        actions = { RowActions(onEdit, onDelete) },
    ) {
        DifficultyChip(task.difficulty)
        task.tagId?.let { ui.tagsById[it] }?.let { TagChip(it) }
        val due = task.dueDate
        when (task.dueStatus(ui.today)) {
            DueStatus.LATE -> MetaText("Atrasada desde ${formatDay(due!!)}", MaterialTheme.colorScheme.error)
            DueStatus.TODAY -> MetaText("Vence hoje", MaterialTheme.colorScheme.tertiary)
            DueStatus.FUTURE -> MetaText("Até ${formatDay(due!!)}")
            null -> {}
        }
        if (task.penalize && task.penalizedAt == null) PenaltyBadge(task.penalty())
    }
}

@Composable
private fun RowScope.RowActions(onEdit: () -> Unit, onDelete: () -> Unit) {
    IconButton(onClick = onEdit) { Text("✎") }
    IconButton(onClick = onDelete) { Text("✕") }
}
