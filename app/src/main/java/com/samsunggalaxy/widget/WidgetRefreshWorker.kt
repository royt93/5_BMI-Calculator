package com.samsunggalaxy.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * EPIC-09 T09.1 — periodic catch-all refresh (date rollover for the 7-day sparkline window,
 * external DB changes). Immediate updates after a save go through
 * WidgetUpdateHelper.updateAllWidgets() directly from RecordSaveHelper instead of waiting for
 * this worker's next tick.
 */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetUpdateHelper.updateAllWidgets(applicationContext)
        return Result.success()
    }
}
