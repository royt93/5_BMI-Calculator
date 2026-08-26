package com.samsunggalaxy.health

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.samsunggalaxy.data.AppDatabase
import com.samsunggalaxy.data.BmiRepository
import com.samsunggalaxy.utils.AppLog
import com.samsunggalaxy.utils.PreferencesManager
import kotlinx.coroutines.flow.first

/** EPIC-09 T09.2 — periodic catch-all sync; re-checks the enabled flag as a safety net in case
 * cancellation raced with an already-queued run (same pattern as ReminderWorker). */
class HealthConnectSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val enabled = PreferencesManager(applicationContext).healthConnectSyncEnabled.first()
        if (!enabled) return Result.success()

        val database = AppDatabase.getDatabase(applicationContext)
        val repository = BmiRepository(database.bmiDao(), database.profileDao(), database.bodyMeasurementDao())
        val profile = repository.getCurrentProfile() ?: return Result.success()

        val result = HealthConnectManager.syncNow(applicationContext, repository, profile.id)
        AppLog.d("HealthConnectSyncWorker: result=$result")
        // Only a real Success counts as "synced" — see SettingsActivity.syncHealthConnectNow for
        // why recording the timestamp on Unavailable/MissingPermissions/Failed is misleading.
        if (result is HealthConnectManager.SyncResult.Success) {
            PreferencesManager(applicationContext).setLastHealthConnectSyncTimestamp(System.currentTimeMillis())
        }
        return Result.success()
    }
}
