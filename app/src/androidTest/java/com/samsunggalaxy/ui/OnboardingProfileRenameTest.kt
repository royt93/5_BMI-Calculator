package com.samsunggalaxy.ui

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.samsunggalaxy.R
import com.samsunggalaxy.data.AppDatabase
import com.samsunggalaxy.data.BmiRepository
import com.samsunggalaxy.data.Profile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * EPIC-05 T05.4: the one-time "What should we call you?" prompt for a profile still named
 * "Default". Needs a connected device/emulator: ./gradlew connectedDevDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class OnboardingProfileRenameTest {

    private lateinit var repository: BmiRepository
    private var testProfileId: Long = -1L
    private var originalCurrentId: Long = 1L
    private val prefsKey = "onboarding_profile_asked"

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        val db = AppDatabase.getDatabase(context)
        repository = BmiRepository(db.bmiDao(), db.profileDao(), db.bodyMeasurementDao())
        originalCurrentId = runBlocking { repository.getCurrentProfile()?.id ?: 1L }
        testProfileId = runBlocking { repository.insertProfile(Profile(name = "Default", isCurrent = false)) }
        runBlocking { repository.setCurrentProfile(testProfileId) }

        // Force the "never asked yet" state for this run.
        context.getSharedPreferences("main_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean(prefsKey, false).apply()
    }

    @After
    fun tearDown() = runBlocking {
        repository.setCurrentProfile(originalCurrentId)
        repository.deleteProfileWithRecords(Profile(id = testProfileId, name = "cleanup", isCurrent = false))
    }

    @Test
    fun onboardingDialog_appearsForDefaultNamedProfile_andRenameSticks() {
        val newName = "OnboardingTest_${System.nanoTime()}"

        ActivityScenario.launch(MainAct::class.java).use {
            Thread.sleep(1500) // loadCurrentProfileAndRefresh() -> maybeShowOnboardingRename()
            onView(withId(R.id.etProfileName)).perform(replaceText(newName))
            onView(withText(R.string.save)).perform(click())
            Thread.sleep(1000)
        }

        val renamed = runBlocking { repository.getCurrentProfile() }
        assertEquals(newName, renamed?.name)

        val askedFlagNowSet = context.getSharedPreferences("main_prefs", Context.MODE_PRIVATE)
            .getBoolean(prefsKey, false)
        assertTrue("flag must be set after the dialog is shown once, so it never nags again", askedFlagNowSet)
    }

    @Test
    fun emptyNameSubmit_doesNotConsumeAskedFlag_soDialogCanReappear() {
        // Regression (audit finding): submitting an empty name used to still mark the
        // "asked" flag (set unconditionally at function entry), permanently losing the
        // onboarding opportunity even though nothing was actually saved or skipped.
        ActivityScenario.launch(MainAct::class.java).use {
            Thread.sleep(1500)
            onView(withId(R.id.etProfileName)).perform(replaceText(""))
            onView(withText(R.string.save)).perform(click())
            Thread.sleep(500)
        }

        val stillDefault = runBlocking { repository.getCurrentProfile()?.name }
        assertEquals("Default", stillDefault)

        val askedFlagSet = context.getSharedPreferences("main_prefs", Context.MODE_PRIVATE)
            .getBoolean(prefsKey, false)
        assertFalse("empty submit must NOT consume the flag — dialog should be able to reappear", askedFlagSet)
    }
}
