package com.samsunggalaxy.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.samsunggalaxy.R
import com.samsunggalaxy.data.AppDatabase
import com.samsunggalaxy.data.BmiRepository
import com.samsunggalaxy.utils.PreferencesManager
import com.samsunggalaxy.utils.UnitFormatter
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
            // Idea I6 — Quick-log Notification Action: only offer "Log same weight" when there's
            // an actual baseline weight to re-log (matches quick-log FAB's own guard).
            val database = AppDatabase.getDatabase(applicationContext)
            val repository = BmiRepository(database.bmiDao(), database.profileDao(), database.bodyMeasurementDao())
            val last = repository.getCurrentProfileMostRecentRecord()
            val quickLogActionLabel = last?.let {
                val unitSystem = PreferencesManager(applicationContext).unitSystem.first()
                val weightText = UnitFormatter.formatWeight(it.weight, unitSystem)
                applicationContext.getString(R.string.notification_quick_log_action, weightText)
            }
            NotificationHelper.showReminder(applicationContext, quickLogActionLabel)
        }
        return Result.success()
    }
}
