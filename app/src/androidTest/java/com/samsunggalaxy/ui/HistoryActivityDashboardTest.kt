package com.samsunggalaxy.ui

import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.samsunggalaxy.R
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
 * Widget/integration tests for the unified Weight Dashboard (EPIC-07): series switcher,
 * the corrected goal-line/goal-row logic, empty state, and the quick-log FAB.
 * Needs a connected device/emulator: ./gradlew connectedDevDebugAndroidTest
 *
 * Isolation: `bmi_database` is the real, persistent app database (not in-memory) — running
 * against the shared "current profile" caused cross-run/cross-test pollution (leftover
 * records from earlier runs skewed "first/last record" assertions). Every test here gets
 * its own throwaway Profile (fresh autoIncrement id, never reused) created in @Before and
 * torn down in @After, so no test can see another test's — or another run's — data.
 */
@RunWith(AndroidJUnit4::class)
class HistoryActivityDashboardTest {

    private lateinit var repository: BmiRepository
    private var testProfileId: Long = -1L
    private var originalProfileId: Long = 1L
    private val dayMs = 86_400_000L

    private fun record(timestamp: Long, weight: Double, height: Double = 175.0) = BmiRecord(
        timestamp = timestamp,
        height = height,
        weight = weight,
        gender = 0,
        age = 30,
        bmi = computeBmi(weight, height),
        bmr = 1500.0,
        tdee = 1800.0,
        idealWeightMin = 60.0,
        idealWeightMax = 75.0,
        bodyFatPercentage = null,
        profileId = testProfileId
    )

    private fun computeBmi(weight: Double, height: Double) = weight / ((height / 100.0) * (height / 100.0))

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = AppDatabase.getDatabase(context)
        repository = BmiRepository(db.bmiDao(), db.profileDao(), db.bodyMeasurementDao())

        originalProfileId = repository.getCurrentProfile()?.id ?: 1L
        testProfileId = repository.insertProfile(
            Profile(name = "DashboardTestProfile_${System.nanoTime()}", isCurrent = false)
        )
        repository.setCurrentProfile(testProfileId)
    }

    @After
    fun tearDown() = runBlocking {
        repository.setCurrentProfile(originalProfileId)
        repository.deleteProfile(Profile(id = testProfileId, name = "cleanup", isCurrent = false))
    }

    @Test
    fun goalRow_lossGoalInProgress_showsRemainingAndEta() {
        // Deterministic linear trend: 80 -> 78 -> 76 kg over 2 days, goal 70kg (still loss-in-progress).
        runBlocking {
            val db = AppDatabase.getDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
            db.bmiDao().insert(record(0L, 80.0))
            db.bmiDao().insert(record(dayMs, 78.0))
            db.bmiDao().insert(record(2 * dayMs, 76.0))
            repository.updateGoalWeight(testProfileId, 70.0)
        }

        ActivityScenario.launch(HistoryActivity::class.java).use { scenario ->
            Thread.sleep(1000)
            scenario.onActivity { activity ->
                val tvGoalSummary = activity.findViewById<TextView>(R.id.tvGoalSummary)
                val tvGoalEta = activity.findViewById<TextView>(R.id.tvGoalEta)
                assertTrue(
                    "goal summary should mention remaining kg, not 'achieved' (was: ${tvGoalSummary.text})",
                    tvGoalSummary.text.toString() != activity.getString(R.string.goal_weight_achieved)
                )
                assertTrue(
                    "expected an ETA estimate for a clean 3-point linear trend (was GONE)",
                    tvGoalEta.visibility == android.view.View.VISIBLE
                )
            }
        }
    }

    @Test
    fun emptyState_shownForProfileWithNoRecords() {
        // testProfileId has zero records straight out of @Before — no extra seeding needed.
        ActivityScenario.launch(HistoryActivity::class.java).use { scenario ->
            Thread.sleep(500)
            scenario.onActivity { activity ->
                val emptyState = activity.findViewById<android.view.View>(R.id.emptyStateContainer)
                val chart = activity.findViewById<android.view.View>(R.id.lineChart)
                assertTrue(emptyState.visibility == android.view.View.VISIBLE)
                assertTrue(chart.visibility != android.view.View.VISIBLE)
            }
        }
    }

    @Test
    fun quickLogFab_addsNewRecord_reusingLastKnownHeight() {
        runBlocking {
            val db = AppDatabase.getDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
            db.bmiDao().insert(record(System.currentTimeMillis() - dayMs, 72.0, height = 180.0))
        }

        val before = runBlocking { repository.getMostRecentRecord(testProfileId) }
        assertEquals(72.0, before!!.weight, 0.001)

        ActivityScenario.launch(HistoryActivity::class.java).use {
            Thread.sleep(1500)
            onView(withId(R.id.fabQuickLog)).perform(click())
            onView(withId(R.id.etQuickLogWeight)).perform(clearText(), typeText("71.2"), closeSoftKeyboard())
            onView(withText(R.string.quick_log_save)).perform(click())
            Thread.sleep(1000)
        }

        val after = runBlocking { repository.getMostRecentRecord(testProfileId) }
        assertEquals(71.2, after!!.weight, 0.001)
        assertEquals("quick-log must reuse the last known height, not reset it", 180.0, after.height, 0.001)
    }
}
