package com.samsunggalaxy.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** EPIC-08 T08.1 — schedules/cancels the daily weigh-in reminder via WorkManager. */
object ReminderScheduler {
    private const val UNIQUE_WORK_NAME = "daily_weigh_in_reminder"

    /**
     * Pure — exposed for unit testing. Milliseconds until the next occurrence of hour:minute
     * at or after nowMillis (today if still upcoming, otherwise tomorrow).
     */
    fun computeInitialDelayMs(hour: Int, minute: Int, nowMillis: Long): Long {
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val target = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }

    fun schedule(context: Context, hour: Int, minute: Int) {
        val initialDelayMs = computeInitialDelayMs(hour, minute, System.currentTimeMillis())

        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .build()

        // CANCEL_AND_REENQUEUE, not UPDATE — WorkManager's UPDATE policy is not guaranteed to
        // apply a new initial delay to periodic work that has already fired at least once, so
        // changing the reminder time in Settings could silently keep firing at the old time.
        // This worker has no retry/backoff state worth preserving, so the clean re-enqueue costs nothing.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}
