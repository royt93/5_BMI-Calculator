package com.samsunggalaxy.ui

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * EPIC-05 T05.3: streak must be scoped per profile (family members tracked via separate
 * profiles must not share/clobber each other's streak), and any pre-multi-profile legacy
 * data must migrate into exactly one profile, not silently vanish.
 * Needs a connected device/emulator: ./gradlew connectedDevDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class StreakManagerProfileScopingTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    // Unique profile ids per test run (System.nanoTime()) so tests never collide with each
    // other or with real app data — SharedPreferences files persist across test runs.
    private fun freshProfileId() = System.nanoTime()

    @Test
    fun twoProfiles_haveIndependentStreaks() {
        val profileA = freshProfileId()
        val profileB = freshProfileId() + 1

        StreakManager.recordCheck(context, profileA)
        StreakManager.recordCheck(context, profileA) // same day, no-op second call

        val dataA = StreakManager.getStreakData(context, profileA)
        val dataB = StreakManager.getStreakData(context, profileB)

        assertEquals(1, dataA.current)
        assertEquals(0, dataB.current) // profile B untouched by profile A's check-in
    }

    @Test
    fun legacyPrefs_migrateIntoFirstProfileThatReads_thenAreConsumed() {
        val legacy = context.getSharedPreferences("streak_prefs", Context.MODE_PRIVATE)
        legacy.edit().clear()
            .putInt("current_streak", 9)
            .putInt("best_streak", 15)
            .putString("last_check_date", java.time.LocalDate.now().toString())
            .commit()

        val profileA = freshProfileId()
        val profileB = freshProfileId() + 1

        val dataA = StreakManager.getStreakData(context, profileA)
        assertEquals("legacy streak must migrate into the first profile that asks", 9, dataA.current)
        assertEquals(15, dataA.best)

        // Legacy is consumed after the first migration — a second, different profile must
        // NOT also inherit it (that would duplicate one person's streak across two people).
        val dataB = StreakManager.getStreakData(context, profileB)
        assertEquals("legacy must be consumed after first migration, not re-copied", 0, dataB.current)

        legacy.edit().clear().apply() // test hygiene
    }

    @Test
    fun clearProfileData_removesTheProfileScopedFile_notOthers() {
        val profileA = freshProfileId()
        val profileB = freshProfileId() + 1
        StreakManager.recordCheck(context, profileA)
        StreakManager.recordCheck(context, profileB)

        StreakManager.clearProfileData(context, profileA)

        assertEquals("cleared profile must read back as empty", 0, StreakManager.getStreakData(context, profileA).current)
        assertEquals("clearing profile A must not touch profile B", 1, StreakManager.getStreakData(context, profileB).current)
    }
}
