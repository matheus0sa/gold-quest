package com.goldquest.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.goldquest.data.Tag
import com.goldquest.domain.DraftKind
import com.goldquest.domain.Repeat
import com.goldquest.domain.TaskDraft
import com.goldquest.domain.isMonthly
import com.goldquest.domain.weekdayList

@Composable
fun EditTaskScreen(id: Long, tags: List<Tag>, vm: MainViewModel, onBack: () -> Unit) {
    var initial by remember { mutableStateOf<TaskDraft?>(null) }
    LaunchedEffect(id) {
        val task = vm.loadTask(id)
        if (task == null || task.done) {
            onBack()
        } else {
            initial = TaskDraft(
                title = task.title, difficulty = task.difficulty, penalize = task.penalize,
                tagId = task.tagId, dueDate = task.dueDate,
            )
        }
    }
    EditScaffold("Editar tarefa", onBack) {
        initial?.let { draft ->
            TaskForm(DraftKind.TASK, draft, tags, "Salvar", vm) { vm.updateTask(id, it, onBack) }
        }
    }
}

@Composable
fun EditRecurringScreen(id: Long, tags: List<Tag>, vm: MainViewModel, onBack: () -> Unit) {
    var initial by remember { mutableStateOf<TaskDraft?>(null) }
    LaunchedEffect(id) {
        val rec = vm.loadRecurring(id)
        if (rec == null) {
            onBack()
        } else {
            initial = TaskDraft(
                title = rec.title, difficulty = rec.difficulty, penalize = rec.penalize, tagId = rec.tagId,
                repeat = if (rec.isMonthly) Repeat.MONTHLY else Repeat.WEEKLY,
                weekdays = if (rec.isMonthly) emptySet() else rec.weekdayList().toSet(),
            )
        }
    }
    EditScaffold("Editar tarefa recorrente", onBack) {
        initial?.let { draft ->
            TaskForm(DraftKind.RECURRING, draft, tags, "Salvar", vm) { vm.updateRecurring(id, it, onBack) }
        }
    }
}

@Composable
private fun EditScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Voltar") }
        ScreenTitle(title)
        Column(Modifier.padding(top = 8.dp)) { content() }
    }
}
