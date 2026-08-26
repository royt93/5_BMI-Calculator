package com.samsunggalaxy.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * EPIC-09 T09.1 — home-screen widget showing latest weight/BMI + 7-day sparkline. All actual
 * work (Room read, RemoteViews build) lives in WidgetUpdateHelper so WidgetRefreshWorker and
 * RecordSaveHelper can trigger the same update path without going through this receiver.
 *
 * BroadcastReceiver.onUpdate() runs on the main thread and must not block on Room I/O; this is
 * the canonical goAsync() pattern for async work in a receiver instead of `lifecycleScope`
 * (BroadcastReceivers have no lifecycle of their own to hang a scope off).
 */
class BmiWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val views = WidgetUpdateHelper.buildRemoteViews(context)
                appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, views) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshScheduler.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefreshScheduler.cancel(context)
    }
}
