package com.samsunggalaxy.health

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** EPIC-09 T09.2 — mirrors ReminderScheduler's pattern for the periodic Health Connect sync. */
object HealthConnectSyncScheduler {
    private const val UNIQUE_WORK_NAME = "health_connect_periodic_sync"
    private const val ONE_TIME_WORK_NAME = "health_connect_one_time_sync"
    private const val SYNC_INTERVAL_HOURS = 6L

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<HealthConnectSyncWorker>(SYNC_INTERVAL_HOURS, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    /**
     * EPIC-09 T09.2 — triggered right after a new local weigh-in is saved (RecordSaveHelper),
     * so the sync doesn't have to wait up to 6h for the periodic worker to pick it up. Runs via
     * WorkManager instead of being awaited inline in the save path — Health Connect I/O (up to
     * 30 days of records, several DB round-trips) has no bearing on whether the save itself
     * succeeded and shouldn't delay the save confirmation the user sees.
     */
    fun enqueueOneTimeSync(context: Context) {
        val request = OneTimeWorkRequestBuilder<HealthConnectSyncWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
