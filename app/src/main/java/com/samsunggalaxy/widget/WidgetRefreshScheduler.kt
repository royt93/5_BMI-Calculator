package com.samsunggalaxy.widget

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** EPIC-09 T09.1 — mirrors ReminderScheduler's pattern for the widget's periodic refresh. */
object WidgetRefreshScheduler {
    private const val UNIQUE_WORK_NAME = "widget_periodic_refresh"
    private const val REFRESH_INTERVAL_HOURS = 6L

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(REFRESH_INTERVAL_HOURS, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}
