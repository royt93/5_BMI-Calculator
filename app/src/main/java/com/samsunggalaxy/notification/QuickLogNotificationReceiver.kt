package com.samsunggalaxy.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.samsunggalaxy.R
import com.samsunggalaxy.data.AppDatabase
import com.samsunggalaxy.data.BmiRepository
import com.samsunggalaxy.ui.RecordSaveHelper
import com.samsunggalaxy.utils.AppLog
import com.samsunggalaxy.utils.CalculatorUtils
import com.samsunggalaxy.utils.PreferencesManager
import com.samsunggalaxy.utils.UnitFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Idea I6 — Quick-log Notification Action: the reminder notification's "Log same weight" button
 * re-logs the profile's latest known weight without opening the app, matching the idea spec's
 * own guidance to avoid free-text entry accuracy issues on a small notification surface
 * ("log lại số gần nhất ±0.1kg thay vì nhập tự do").
 *
 * goAsync() + a fire-and-forget coroutine (not WorkManager) — this is a short, immediate DB
 * write triggered by an explicit user tap, not deferred/retriable background work like
 * ReminderWorker's daily schedule, so the extra WorkManager machinery isn't warranted.
 */
class QuickLogNotificationReceiver : BroadcastReceiver() {

    companion object {
        // A BroadcastReceiver gets a fresh instance per dispatch, so this guard has to live at
        // object/companion scope (not an instance field) to catch a rapid double-tap on the
        // notification action firing the PendingIntent twice before the first insert completes.
        @Volatile
        private var isProcessing = false
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (isProcessing) return
        isProcessing = true

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getDatabase(context)
                val repository = BmiRepository(database.bmiDao(), database.profileDao(), database.bodyMeasurementDao())
                val profile = repository.getCurrentProfile()
                val last = profile?.let { repository.getMostRecentRecord(it.id) }
                if (last != null) {
                    val prefs = PreferencesManager(context)
                    val activityLevel = prefs.activityLevel.first()
                    val unitSystem = prefs.unitSystem.first()
                    val record = CalculatorUtils.buildQuickLogRecord(last, last.weight, activityLevel)

                    val newlyEarned = RecordSaveHelper.saveAndCheckBadges(
                        context = context,
                        repository = repository,
                        record = record,
                        goalWeight = profile.goalWeight
                    )

                    // Mirror HistoryActivity.quickLogWeight()'s Snackbar: a newly-earned badge
                    // takes priority over the plain "weight logged" confirmation.
                    val bodyText = newlyEarned.firstOrNull()?.let { badge ->
                        "🎉 ${context.getString(badge.titleRes)}!"
                    } ?: context.getString(
                        R.string.notification_quick_log_confirmation_body,
                        UnitFormatter.formatWeight(record.weight, unitSystem)
                    )
                    NotificationHelper.showQuickLogConfirmation(context, bodyText)
                } else {
                    // No baseline weight to re-log — same guard as the quick-log FAB. Nothing to
                    // insert; leave the original reminder notification as-is so the user can
                    // still tap it to open the app and log their first entry through the wizard.
                    AppLog.w("QuickLogNotificationReceiver: no baseline record, ignoring tap")
                }
            } catch (e: Exception) {
                AppLog.w("QuickLogNotificationReceiver error", e)
            } finally {
                isProcessing = false
                pendingResult.finish()
            }
        }
    }
}
