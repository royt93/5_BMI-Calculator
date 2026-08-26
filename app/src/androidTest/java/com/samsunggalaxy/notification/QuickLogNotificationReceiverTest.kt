package com.samsunggalaxy.notification

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.samsunggalaxy.data.AppDatabase
import com.samsunggalaxy.data.BmiRecord
import com.samsunggalaxy.data.BmiRepository
import com.samsunggalaxy.data.Profile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Idea I6 — Quick-log Notification Action. Sends a real broadcast to
 * QuickLogNotificationReceiver (not a direct onReceive() call — goAsync() requires actual
 * dispatch through the manifest-registered receiver) and polls the DB for the new record,
 * since the receiver's DB write happens on a background coroutine after the broadcast returns.
 * Needs a connected device/emulator: ./gradlew connectedDevDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class QuickLogNotificationReceiverTest {

    private lateinit var context: Context
    private lateinit var repository: BmiRepository
    private var profileId: Long = -1L
    private var originalProfileId: Long = 1L

    @Before
    fun setUp(): Unit = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        // Same reasoning as EngagementFeaturesTest — AGP uninstalls/reinstalls between runs,
        // wiping any prior adb grant, so this survives that and skips the system permission dialog.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName, android.Manifest.permission.POST_NOTIFICATIONS
            )
        }
        val db = AppDatabase.getDatabase(context)
        repository = BmiRepository(db.bmiDao(), db.profileDao(), db.bodyMeasurementDao())
        originalProfileId = repository.getCurrentProfile()?.id ?: 1L
        profileId = repository.insertProfile(Profile(name = "QuickLogNotifTest_${System.nanoTime()}", isCurrent = false))
        repository.setCurrentProfile(profileId)
        // All tests here share NotificationHelper's single NOTIFICATION_ID — clear any leftover
        // notification (e.g. a confirmation posted by a previous test's still-settling coroutine)
        // so showReminder_withQuickLogLabel_postsNotificationWithAction isn't racing stale state.
        context.getSystemService(NotificationManager::class.java)?.cancelAll()
    }

    @After
    fun tearDown() = runBlocking {
        repository.setCurrentProfile(originalProfileId)
        repository.deleteProfileWithRecords(Profile(id = profileId, name = "cleanup", isCurrent = false))
    }

    private fun baselineRecord() = BmiRecord(
        timestamp = System.currentTimeMillis() - 86_400_000L,
        height = 170.0,
        weight = 72.0,
        gender = 0,
        age = 28,
        bmi = 72.0 / (1.7 * 1.7),
        bmr = 1600.0,
        tdee = 2100.0,
        idealWeightMin = 60.0,
        idealWeightMax = 75.0,
        bodyFatPercentage = null,
        profileId = profileId
    )

    @Test
    fun tappingAction_insertsNewRecord_withSameWeightAsBaseline(): Unit = runBlocking {
        repository.insertRecord(baselineRecord())
        val before = repository.getRecordCount(profileId)

        context.sendBroadcast(Intent(context, QuickLogNotificationReceiver::class.java))

        val after = pollRecordCount(before)
        assertEquals(before + 1, after)
        // The DB write is the first thing the receiver's coroutine does after saveAndCheckBadges
        // starts — badge checks + the confirmation-notification post still run after this count
        // changes. Wait for those to settle too, so the next test's notification assertions
        // (shared NOTIFICATION_ID) don't race a still-in-flight post from this test.
        kotlinx.coroutines.delay(500)

        val latest = repository.getCurrentProfileMostRecentRecord()!!
        assertEquals(72.0, latest.weight, 0.001)
        assertEquals(profileId, latest.profileId)
    }

    /**
     * Two back-to-back sendBroadcast() calls from a test thread don't reliably reproduce a real
     * rapid double-tap — the Binder IPC round-trip between them is usually slower than this
     * receiver's whole DB-write, so the first coroutine often finishes before the second
     * broadcast even arrives, and both legitimately succeed (which is correct — two genuinely
     * sequential taps should both log). To deterministically test the actual overlap case, set
     * the guard flag directly via reflection to simulate "still processing the first tap".
     */
    @Test
    fun tappingAction_whileAlreadyProcessing_isIgnored(): Unit = runBlocking {
        repository.insertRecord(baselineRecord())
        val before = repository.getRecordCount(profileId)

        // Kotlin compiles a private, non-@JvmStatic companion property with no getter/setter
        // usage outside the class as a plain static field on the OUTER class, not on the
        // Companion class itself — confirmed via dexdump.
        val isProcessingField = QuickLogNotificationReceiver::class.java.getDeclaredField("isProcessing")
        isProcessingField.isAccessible = true
        isProcessingField.setBoolean(null, true)
        try {
            context.sendBroadcast(Intent(context, QuickLogNotificationReceiver::class.java))
            Thread.sleep(1000) // give a wrongly-accepted tap a chance to land, if the guard failed
            assertEquals(before, repository.getRecordCount(profileId))
        } finally {
            isProcessingField.setBoolean(null, false)
        }
    }

    @Test
    fun tappingAction_noBaselineRecord_doesNotInsertAnything(): Unit = runBlocking {
        val before = repository.getRecordCount(profileId)

        context.sendBroadcast(Intent(context, QuickLogNotificationReceiver::class.java))
        Thread.sleep(1500) // give the receiver's coroutine a chance to run and confirm nothing changes

        assertEquals(before, repository.getRecordCount(profileId))
    }

    @Test
    fun showReminder_withQuickLogLabel_postsNotificationWithAction() {
        NotificationHelper.showReminder(context, quickLogActionLabel = "Log 72.0 kg")

        // NotificationManagerCompat.notify() is fire-and-forget (Binder IPC to system_server) —
        // it can return before getActiveNotifications() reflects the post, so poll instead of
        // asserting immediately.
        val manager = context.getSystemService(NotificationManager::class.java)!!
        var posted = manager.activeNotifications.firstOrNull { it.notification.actions?.isNotEmpty() == true }
        var attempts = 0
        while (posted == null && attempts < 20) {
            Thread.sleep(100)
            posted = manager.activeNotifications.firstOrNull { it.notification.actions?.isNotEmpty() == true }
            attempts++
        }
        assertTrue("expected a posted notification with at least one action", posted != null)
        assertEquals("Log 72.0 kg", posted!!.notification.actions[0].title)

        manager.cancelAll()
    }

    /** Polls up to 3s — the receiver's DB write happens on a background coroutine, not synchronously. */
    private suspend fun pollRecordCount(before: Int): Int {
        repeat(30) {
            val count = repository.getRecordCount(profileId)
            if (count != before) return count
            kotlinx.coroutines.delay(100)
        }
        return repository.getRecordCount(profileId)
    }
}
