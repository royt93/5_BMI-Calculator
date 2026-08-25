package com.samsunggalaxy.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.samsunggalaxy.utils.PreferencesManager
import kotlinx.coroutines.flow.first

/**
 * EPIC-08 T08.1 — fires daily via WorkManager (Doze-safe, unlike a raw AlarmManager broadcast).
 * Re-checks the enabled flag itself as a safety net in case cancellation raced with a
 * already-queued run.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val enabled = PreferencesManager(applicationContext).reminderEnabled.first()
        if (enabled) {
            NotificationHelper.showReminder(applicationContext)
        }
        return Result.success()
    }
}
