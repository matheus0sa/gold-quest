package com.goldquest.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.goldquest.GoldQuestApp
import com.goldquest.MainActivity
import com.goldquest.R
import com.goldquest.domain.ReminderSummary
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Preferências do lembrete diário (guardadas à parte do banco, são do aparelho). */
class ReminderPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("reminder", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    var hour: Int
        get() = prefs.getInt("hour", DEFAULT_HOUR)
        set(value) = prefs.edit().putInt("hour", value).apply()

    var minute: Int
        get() = prefs.getInt("minute", 0)
        set(value) = prefs.edit().putInt("minute", value).apply()

    private companion object {
        const val DEFAULT_HOUR = 20
    }
}

object ReminderScheduler {
    private const val WORK_NAME = "daily_reminder"

    /** Atraso até a próxima ocorrência de hour:minute (hoje se ainda não passou, senão amanhã). */
    fun delayUntil(hour: Int, minute: Int, now: LocalDateTime = LocalDateTime.now()): Duration {
        var next = now.toLocalDate().atTime(hour, minute)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }

    /**
     * Agenda o lembrete diário. [reset] recomeça a contagem (use ao mudar o horário);
     * sem ele, um agendamento que já existe é mantido.
     */
    fun schedule(context: Context, hour: Int, minute: Int, reset: Boolean) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntil(hour, minute))
            .build()
        val policy = if (reset) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, policy, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!ReminderPrefs(applicationContext).enabled) return Result.success()
        val repository = (applicationContext as GoldQuestApp).repository
        val summary = repository.reminderSummary(LocalDate.now())
        if (summary.total > 0) Notifier.show(applicationContext, summary)
        return Result.success()
    }
}

object Notifier {
    private const val CHANNEL_ID = "reminders"
    private const val NOTIFICATION_ID = 1

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Lembrete diário", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "Avisa o que ainda falta fazer hoje para juntar gold"
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun describe(summary: ReminderSummary): String {
        val parts = buildList {
            if (summary.habits > 0) add(plural(summary.habits, "hábito de hoje", "hábitos de hoje"))
            if (summary.monthly > 0) add(plural(summary.monthly, "tarefa mensal", "tarefas mensais"))
            if (summary.tasksDue > 0) {
                add(plural(summary.tasksDue, "tarefa com prazo hoje ou atrasada", "tarefas com prazo hoje ou atrasadas"))
            }
        }
        val list = if (parts.size > 1) parts.dropLast(1).joinToString(", ") + " e " + parts.last() else parts.single()
        return "Falta: $list. Hora de juntar gold!"
    }

    private fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

    fun show(context: Context, summary: ReminderSummary) {
        if (!canNotify(context)) return
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = describe(summary)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Gold Quest")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(0xFFE8A817.toInt())
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
