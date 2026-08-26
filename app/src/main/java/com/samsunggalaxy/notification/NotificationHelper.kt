package com.samsunggalaxy.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.samsunggalaxy.R
import com.samsunggalaxy.ui.MainAct

/** EPIC-08 T08.1 — daily weigh-in reminder notification. */
object NotificationHelper {
    const val CHANNEL_ID = "weigh_in_reminder"
    private const val NOTIFICATION_ID = 1001

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.reminder_notification_channel_desc)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * [quickLogActionLabel] — Idea I6: when the caller already knows the profile's latest
     * weight (formatted, e.g. "80.0 kg"), pass a label to add a "Log same weight" action button
     * that fires straight to QuickLogNotificationReceiver without opening the app. Null when
     * there's no baseline weight yet to re-log (matches quick-log FAB's own guard).
     */
    fun showReminder(context: Context, quickLogActionLabel: String? = null) {
        ensureChannel(context)

        val intent = Intent(context, MainAct::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(context.getString(R.string.reminder_notification_body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (quickLogActionLabel != null) {
            val quickLogIntent = Intent(context, QuickLogNotificationReceiver::class.java)
            val quickLogPendingIntent = PendingIntent.getBroadcast(
                context, 0, quickLogIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_notification_bell, quickLogActionLabel, quickLogPendingIntent)
        }

        // POST_NOTIFICATIONS is declared in the manifest and requested at toggle-on time
        // (SettingsActivity); this check is a defensive no-op instead of a crash if the OS
        // permission was revoked after scheduling (Settings > Apps > Notifications > off).
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        }
    }

    /** Idea I6 — replaces the reminder notification with a brief confirmation after a quick-log. */
    fun showQuickLogConfirmation(context: Context, bodyText: String) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setContentTitle(context.getString(R.string.notification_quick_log_confirmation_title))
            .setContentText(bodyText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setTimeoutAfter(TIMEOUT_MS)
            .build()
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    private const val TIMEOUT_MS = 10_000L
}
