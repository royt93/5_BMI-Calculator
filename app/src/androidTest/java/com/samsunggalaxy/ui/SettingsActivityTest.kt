package com.samsunggalaxy.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.samsunggalaxy.R
import com.samsunggalaxy.data.AppDatabase
import com.samsunggalaxy.data.BmiRecord
import com.samsunggalaxy.data.BmiRepository
import com.samsunggalaxy.data.Profile
import com.samsunggalaxy.utils.PreferencesManager
import com.samsunggalaxy.utils.ThemeHelper
import com.samsunggalaxy.utils.UnitFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * EPIC-04: unit system toggle, theme toggle, and clear-history all wired to
 * PreferencesManager/AppCompatDelegate/BmiRepository from the real Settings screen.
 * Needs a connected device/emulator: ./gradlew connectedDevDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class SettingsActivityTest {

    private lateinit var prefs: PreferencesManager
    private lateinit var repository: BmiRepository
    private var testProfileId: Long = -1L
    private var otherProfileId: Long = -1L
    private var originalProfileId: Long = 1L

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        prefs = PreferencesManager(context)
        val db = AppDatabase.getDatabase(context)
        repository = BmiRepository(db.bmiDao(), db.profileDao())
        originalProfileId = runBlocking { repository.getCurrentProfile()?.id ?: 1L }
        runBlocking {
            testProfileId = repository.insertProfile(Profile(name = "SettingsTestProfile_${System.nanoTime()}"))
            otherProfileId = repository.insertProfile(Profile(name = "SettingsTestOther_${System.nanoTime()}"))
            repository.setCurrentProfile(testProfileId)
        }
    }

    @After
    fun tearDown() = runBlocking {
        repository.setCurrentProfile(originalProfileId)
        repository.deleteProfileWithRecords(Profile(id = testProfileId, name = "cleanup", isCurrent = false))
        repository.deleteProfileWithRecords(Profile(id = otherProfileId, name = "cleanup", isCurrent = false))
        prefs.setUnitSystem(UnitFormatter.METRIC)
        ThemeHelper.apply(ThemeHelper.SYSTEM)
        prefs.setThemeMode(ThemeHelper.SYSTEM)
    }

    @Test
    fun tappingImperial_persistsUnitSystem() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            Thread.sleep(500)
            onView(withId(R.id.btnUnitImperial)).perform(click())
            Thread.sleep(300)
        }
        val persisted = runBlocking { prefs.unitSystem.first() }
        assertEquals(UnitFormatter.IMPERIAL, persisted)
    }

    @Test
    fun tappingDark_appliesImmediatelyAndPersists() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            Thread.sleep(500)
            onView(withId(R.id.btnThemeDark)).perform(click())
            Thread.sleep(300)
        }
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode())
        val persisted = runBlocking { prefs.themeMode.first() }
        assertEquals(ThemeHelper.DARK, persisted)
    }

    @Test
    fun clearHistory_wipesRecordsForCurrentProfileOnly() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = AppDatabase.getDatabase(context)
        runBlocking {
            db.bmiDao().insert(record(testProfileId))
            db.bmiDao().insert(record(testProfileId))
            db.bmiDao().insert(record(otherProfileId)) // different profile — must survive
        }
        assertEquals(2, runBlocking { repository.getRecordCount(testProfileId) })

        ActivityScenario.launch(SettingsActivity::class.java).use {
            Thread.sleep(500)
            onView(withId(R.id.clearHistoryContainer)).perform(click())
            Thread.sleep(300)
            onView(withText(R.string.clear_history)).perform(click())
            Thread.sleep(500)
        }

        assertEquals(0, runBlocking { repository.getRecordCount(testProfileId) })
        assertTrue(runBlocking { repository.getRecordCount(otherProfileId) } >= 1)
    }

    private fun record(profileId: Long) = BmiRecord(
        timestamp = System.currentTimeMillis(),
        height = 175.0,
        weight = 70.0,
        gender = 0,
        age = 30,
        bmi = 22.9,
        bmr = 1500.0,
        tdee = 1800.0,
        idealWeightMin = 60.0,
        idealWeightMax = 75.0,
        bodyFatPercentage = null,
        profileId = profileId
    )
}
