package com.goldquest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.goldquest.data.Difficulty
import com.goldquest.data.Tag
import com.goldquest.domain.DraftKind
import com.goldquest.domain.MsgKind
import com.goldquest.domain.Repeat
import com.goldquest.domain.TaskDraft
import com.goldquest.domain.WEEKDAY_LABELS
import com.goldquest.domain.formatDay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Campos de tarefa, compartilhados entre criar e editar.
 * kind: NEW (escolhe prazo ou repetição), TASK (só prazo) ou RECURRING (só repetição).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskForm(
    kind: DraftKind,
    initial: TaskDraft,
    tags: List<Tag>,
    submitLabel: String,
    vm: MainViewModel,
    onSubmit: (TaskDraft) -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    var showTags by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = draft.title,
            onValueChange = { draft = draft.copy(title = it.take(120)) },
            label = { Text("O que precisa ser feito?") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )

        FieldLabel("Dificuldade")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (d in Difficulty.entries) {
                FilterChip(
                    selected = draft.difficulty == d,
                    onClick = { draft = draft.copy(difficulty = d) },
                    label = { Text("${d.label} · ${d.reward} gold") },
                )
            }
        }

        FieldLabel("Tag")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(draft.tagId == null, { draft = draft.copy(tagId = null) }, { Text("Nenhuma") })
            for (tag in tags) {
                FilterChip(draft.tagId == tag.id, { draft = draft.copy(tagId = tag.id) }, { Text(tag.name) })
            }
            TextButton(onClick = { showTags = true }) { Text("Gerenciar") }
        }

        if (kind != DraftKind.TASK) {
            FieldLabel(if (kind == DraftKind.NEW) "Repetir (opcional)" else "Repetir")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (kind == DraftKind.NEW) {
                    FilterChip(draft.repeat == Repeat.NONE, { draft = draft.copy(repeat = Repeat.NONE) }, { Text("Não repete") })
                }
                FilterChip(draft.repeat == Repeat.WEEKLY, { draft = draft.copy(repeat = Repeat.WEEKLY) }, { Text("Dias da semana") })
                FilterChip(draft.repeat == Repeat.MONTHLY, { draft = draft.copy(repeat = Repeat.MONTHLY) }, { Text("1x por mês") })
            }
            if (draft.repeat == Repeat.WEEKLY) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WEEKDAY_LABELS.forEachIndexed { i, label ->
                        FilterChip(
                            selected = i in draft.weekdays,
                            onClick = {
                                draft = draft.copy(weekdays = if (i in draft.weekdays) draft.weekdays - i else draft.weekdays + i)
                            },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { draft = draft.copy(penalize = !draft.penalize) },
        ) {
            Checkbox(draft.penalize, { draft = draft.copy(penalize = it) })
            val needs = when (kind) {
                DraftKind.NEW -> " (precisa de prazo ou repetição)"
                DraftKind.TASK -> " (precisa de prazo)"
                DraftKind.RECURRING -> ""
            }
            Text("Penalidade se não fizer: perde 10% do valor$needs", fontSize = 14.sp)
        }

        val isRecurring = draft.repeat != Repeat.NONE || kind == DraftKind.RECURRING
        if (!isRecurring) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showDate = true }) {
                    Text(draft.dueDate?.let { "Prazo: ${formatDay(it)}" } ?: "Prazo (opcional)")
                }
                if (draft.dueDate != null) {
                    TextButton(onClick = { draft = draft.copy(dueDate = null) }) { Text("Limpar") }
                }
            }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp) }

        Button(
            onClick = {
                // Tarefa que repete não tem prazo; descarta um prazo escolhido antes de ativar a repetição
                val final = if (isRecurring) draft.copy(dueDate = null) else draft.copy(repeat = Repeat.NONE)
                val problem = final.validate(kind)
                error = problem
                if (problem == null) onSubmit(final)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(submitLabel, fontWeight = FontWeight.SemiBold) }
    }

    if (showTags) TagDialog(tags, vm) { showTags = false }
    if (showDate) {
        DueDatePicker(
            current = draft.dueDate,
            onPick = { draft = draft.copy(dueDate = it) },
            onDismiss = { showDate = false },
        )
    }
}

@Composable
private fun FieldLabel(text: String) =
    Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueDatePicker(current: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val initial = (current ?: LocalDate.now()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = initial)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    ) { DatePicker(state) }
}

/** Lista as tags para remover e permite criar novas. Remover uma tag não apaga tarefas. */
@Composable
fun TagDialog(tags: List<Tag>, vm: MainViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun add() {
        scope.launch {
            val msg = vm.addTag(name)
            if (msg.kind == MsgKind.ERROR) error = msg.text else { name = ""; error = null }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tags") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Remover uma tag não apaga tarefas: elas só ficam sem tag.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                for (tag in tags) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tag.name, Modifier.weight(1f))
                        TextButton(onClick = { vm.deleteTag(tag.id) }) {
                            Text("Remover", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30); error = null },
                    label = { Text("Nova tag") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = ::add) { Text("Adicionar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}
