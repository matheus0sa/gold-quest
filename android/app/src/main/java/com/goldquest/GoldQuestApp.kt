package com.goldquest

import android.app.Application
import com.goldquest.data.AppDatabase
import com.goldquest.domain.GoldRepository
import com.goldquest.notify.Notifier
import com.goldquest.notify.ReminderPrefs
import com.goldquest.notify.ReminderScheduler

class GoldQuestApp : Application() {
    val repository: GoldRepository by lazy { GoldRepository(AppDatabase.create(this)) }

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannel(this)
        val prefs = ReminderPrefs(this)
        if (prefs.enabled) ReminderScheduler.schedule(this, prefs.hour, prefs.minute, reset = false)
    }
}
