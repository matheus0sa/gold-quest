package com.goldquest.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldquest.notify.Notifier
import com.goldquest.notify.ReminderPrefs
import com.goldquest.notify.ReminderScheduler

/** Liga/desliga o lembrete diário e escolhe o horário. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { ReminderPrefs(context) }
    var enabled by remember { mutableStateOf(prefs.enabled && Notifier.canNotify(context)) }
    var hour by remember { mutableStateOf(prefs.hour) }
    var minute by remember { mutableStateOf(prefs.minute) }
    var showTime by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }

    fun turnOn() {
        prefs.enabled = true
        ReminderScheduler.schedule(context, hour, minute, reset = true)
        enabled = true
        denied = false
    }

    fun turnOff() {
        prefs.enabled = false
        ReminderScheduler.cancel(context)
        enabled = false
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) turnOn() else denied = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lembrete diário") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Todo dia, no horário escolhido, o app avisa o que ainda falta: hábitos de hoje, " +
                        "tarefas com prazo e mensais perto do fim do mês. Se não faltar nada, não avisa.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Ativar lembrete")
                    Switch(
                        checked = enabled,
                        onCheckedChange = { on ->
                            when {
                                !on -> turnOff()
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifier.canNotify(context) ->
                                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                else -> turnOn()
                            }
                        },
                    )
                }
                OutlinedButton(onClick = { showTime = true }) {
                    Text("Horário: %02d:%02d".format(hour, minute))
                }
                if (denied) {
                    Text(
                        "Sem a permissão de notificações o lembrete não funciona. " +
                            "Libere em Configurações > Apps > Gold Quest > Notificações.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )

    if (showTime) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    hour = state.hour
                    minute = state.minute
                    prefs.hour = hour
                    prefs.minute = minute
                    if (enabled) ReminderScheduler.schedule(context, hour, minute, reset = true)
                    showTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancelar") } },
        )
    }
}
