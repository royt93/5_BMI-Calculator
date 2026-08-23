package com.samsunggalaxy.ui

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * EPIC-05 T05.3: badges must be scoped per profile so family members each earn their own,
 * and any pre-multi-profile legacy data migrates into exactly one profile.
 * Needs a connected device/emulator: ./gradlew connectedDevDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class BadgeManagerProfileScopingTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun freshProfileId() = System.nanoTime()

    @Test
    fun twoProfiles_earnBadgesIndependently() {
        val profileA = freshProfileId()
        val profileB = freshProfileId() + 1

        val earnedByA = BadgeManager.checkAll(
            context = context,
            profileId = profileA,
            recordCount = 1, // triggers FIRST_STEP
            currentBmi = 22.0,
            currentWeight = 70.0,
            goalWeight = null,
            recentBmiList = listOf(22.0)
        )
        assertTrue(earnedByA.contains(BadgeManager.Badge.FIRST_STEP))
        assertTrue(BadgeManager.isEarned(context, profileA, BadgeManager.Badge.FIRST_STEP))
        assertFalse(
            "profile B must not inherit profile A's badge",
            BadgeManager.isEarned(context, profileB, BadgeManager.Badge.FIRST_STEP)
        )
    }

    @Test
    fun legacyBadgePrefs_migrateIntoFirstProfileThatReads_thenAreConsumed() {
        val legacy = context.getSharedPreferences("badge_prefs", Context.MODE_PRIVATE)
        legacy.edit().clear()
            .putBoolean("first_step_earned", true)
            .putLong("first_step_date", 123456789L)
            .commit()

        val profileA = freshProfileId()
        val profileB = freshProfileId() + 1

        assertTrue(BadgeManager.isEarned(context, profileA, BadgeManager.Badge.FIRST_STEP))
        assertFalse(
            "legacy must be consumed after first migration, not re-copied into a second profile",
            BadgeManager.isEarned(context, profileB, BadgeManager.Badge.FIRST_STEP)
        )

        legacy.edit().clear().apply() // test hygiene
    }

    @Test
    fun clearProfileData_removesTheProfileScopedFile_notOthers() {
        val profileA = freshProfileId()
        val profileB = freshProfileId() + 1
        BadgeManager.checkAll(
            context = context, profileId = profileA, recordCount = 1,
            currentBmi = 22.0, currentWeight = 70.0, goalWeight = null, recentBmiList = emptyList()
        )
        BadgeManager.checkAll(
            context = context, profileId = profileB, recordCount = 1,
            currentBmi = 22.0, currentWeight = 70.0, goalWeight = null, recentBmiList = emptyList()
        )

        BadgeManager.clearProfileData(context, profileA)

        assertFalse(BadgeManager.isEarned(context, profileA, BadgeManager.Badge.FIRST_STEP))
        assertTrue(
            "clearing profile A must not touch profile B",
            BadgeManager.isEarned(context, profileB, BadgeManager.Badge.FIRST_STEP)
        )
    }

    @Test
    fun getEarnedCount_scopedPerProfile() {
        val profileA = freshProfileId()
        BadgeManager.checkAll(
            context = context, profileId = profileA, recordCount = 50,
            currentBmi = 22.0, currentWeight = 70.0, goalWeight = null, recentBmiList = emptyList()
        )
        // recordCount=50 unlocks both FIRST_STEP and DATA_LOVER
        assertEquals(2, BadgeManager.getEarnedCount(context, profileA))
    }
}
