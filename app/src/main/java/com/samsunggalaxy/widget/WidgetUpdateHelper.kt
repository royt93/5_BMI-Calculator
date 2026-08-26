package com.samsunggalaxy.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.samsunggalaxy.R
import com.samsunggalaxy.data.AppDatabase
import com.samsunggalaxy.data.BmiRepository
import com.samsunggalaxy.ui.MainAct
import com.samsunggalaxy.utils.CalculatorUtils
import com.samsunggalaxy.utils.PreferencesManager
import com.samsunggalaxy.utils.UnitFormatter
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * EPIC-09 T09.1 — builds the widget's RemoteViews from Room + DataStore. Shared by
 * BmiWidgetProvider.onUpdate (system-triggered) and WidgetRefreshWorker/RecordSaveHelper
 * (app-triggered, e.g. right after a new weigh-in is saved).
 */
object WidgetUpdateHelper {
    private const val SPARKLINE_DAYS = 7L
    private const val SPARKLINE_WIDTH_PX = 400
    private const val SPARKLINE_HEIGHT_PX = 120

    suspend fun updateAllWidgets(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(android.content.ComponentName(context, BmiWidgetProvider::class.java))
        if (ids.isEmpty()) return

        val views = buildRemoteViews(context)
        ids.forEach { id -> manager.updateAppWidget(id, views) }
    }

    suspend fun buildRemoteViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_bmi)

        val openAppIntent = Intent(context, MainAct::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)

        val database = AppDatabase.getDatabase(context)
        val repository = BmiRepository(database.bmiDao(), database.profileDao(), database.bodyMeasurementDao())
        val profile = repository.getCurrentProfile()
        val latest = profile?.let { repository.getMostRecentRecord(it.id) }

        if (profile == null || latest == null) {
            views.setViewVisibility(R.id.widgetEmptyState, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.widgetDataContainer, android.view.View.GONE)
            return views
        }

        views.setViewVisibility(R.id.widgetEmptyState, android.view.View.GONE)
        views.setViewVisibility(R.id.widgetDataContainer, android.view.View.VISIBLE)

        val unitSystem = PreferencesManager(context).unitSystem.first()
        views.setTextViewText(R.id.widgetWeightValue, UnitFormatter.formatWeight(latest.weight, unitSystem))

        val category = CalculatorUtils.getBMICategoryInfo(latest.bmi)
        views.setTextViewText(R.id.widgetBmiValue, context.getString(R.string.widget_bmi_value, latest.bmi))
        views.setTextViewText(R.id.widgetBmiCategory, context.getString(category.labelRes))
        views.setTextColor(R.id.widgetBmiCategory, ContextCompat.getColor(context, category.colorRes))

        val sinceMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(SPARKLINE_DAYS)
        val recent = repository.getRecordsSince(profile.id, sinceMs)
        val sparklineColor = ContextCompat.getColor(context, category.colorRes)
        val bitmap = SparklineRenderer.render(
            recent.map { it.weight },
            SPARKLINE_WIDTH_PX,
            SPARKLINE_HEIGHT_PX,
            sparklineColor
        )
        if (bitmap != null) {
            views.setViewVisibility(R.id.widgetSparkline, android.view.View.VISIBLE)
            views.setImageViewBitmap(R.id.widgetSparkline, bitmap)
        } else {
            views.setViewVisibility(R.id.widgetSparkline, android.view.View.GONE)
        }

        return views
    }
}
