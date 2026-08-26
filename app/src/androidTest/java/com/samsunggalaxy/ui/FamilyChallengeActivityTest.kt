package com.samsunggalaxy.ui

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.samsunggalaxy.R
import com.samsunggalaxy.data.AppDatabase
import com.samsunggalaxy.data.BmiRepository
import com.samsunggalaxy.data.Profile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Idea I4 — Family Challenge Mode: with at least 2 profiles on the device, the leaderboard
 * renders instead of the empty state. The <2-profiles empty-state branch is a trivial pure
 * function (see `isTooFewForLeaderboard` in LeaderboardAdapter.kt, unit-tested there) — it
 * isn't instrumented-tested here because the real `bmi_database` on a dev device already
 * accumulates profiles from other test runs, so "exactly 1 profile" can't be reproduced
 * without destructively deleting profiles that belong to other tests/manual data.
 * Needs a connected device/emulator: ./gradlew connectedDevDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class FamilyChallengeActivityTest {

    private lateinit var repository: BmiRepository
    private var soloProfileId: Long = -1L
    private var siblingProfileId: Long = -1L
    private var originalProfileId: Long = 1L

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = AppDatabase.getDatabase(context)
        repository = BmiRepository(db.bmiDao(), db.profileDao(), db.bodyMeasurementDao())

        originalProfileId = repository.getCurrentProfile()?.id ?: 1L
        soloProfileId = repository.insertProfile(
            Profile(name = "FamilyChallengeSolo_${System.nanoTime()}", isCurrent = false)
        )
    }

    @After
    fun tearDown() = runBlocking {
        repository.setCurrentProfile(originalProfileId)
        repository.deleteProfile(Profile(id = soloProfileId, name = "cleanup", isCurrent = false))
        if (siblingProfileId != -1L) {
            repository.deleteProfile(Profile(id = siblingProfileId, name = "cleanup", isCurrent = false))
        }
    }

    @Test
    fun twoProfiles_showsRankedLeaderboard(): Unit = runBlocking {
        repository.setCurrentProfile(soloProfileId)
        siblingProfileId = repository.insertProfile(
            Profile(name = "FamilyChallengeSibling_${System.nanoTime()}", isCurrent = false)
        )

        ActivityScenario.launch<FamilyChallengeActivity>(
            Intent(InstrumentationRegistry.getInstrumentation().targetContext, FamilyChallengeActivity::class.java)
        ).use {
            onView(withId(R.id.rvLeaderboard)).check(matches(isDisplayed()))
        }
    }
}
