package com.samsunggalaxy.ui

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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end widget test for EPIC-05 T05.1+T05.2: MainAct's profile chip -> the real
 * ProfileSwitcherBottomSheet -> creating a profile -> it becomes current. Repository-level
 * CRUD correctness (rename preserves fields, cascade delete, last-profile guard) is covered
 * separately in ProfileCrudTest — this test exists to prove the UI is actually wired to it.
 * Needs a connected device/emulator: ./gradlew connectedDevDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class ProfileSwitcherE2ETest {

    private val testProfilePrefix = "E2ETest_"
    private lateinit var repository: BmiRepository
    private var originalCurrentId: Long = 1L

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = AppDatabase.getDatabase(context)
        repository = BmiRepository(db.bmiDao(), db.profileDao())
        originalCurrentId = runBlocking { repository.getCurrentProfile()?.id ?: 1L }
        // This test isn't about onboarding (see OnboardingProfileRenameTest) — pre-set the
        // flag so MainAct's modal "What should we call you?" dialog doesn't cover the chip.
        context.getSharedPreferences("main_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("onboarding_profile_asked", true).apply()
    }

    @After
    fun tearDown() = runBlocking {
        repository.setCurrentProfile(originalCurrentId)
        repository.getAllProfilesOnce()
            .filter { it.name.startsWith(testProfilePrefix) }
            .forEach { repository.deleteProfileWithRecords(it) }
    }

    @Test
    fun createProfileFromChip_appearsAsNewCurrentProfile() {
        val newName = "${testProfilePrefix}${System.nanoTime()}"

        ActivityScenario.launch(MainAct::class.java).use {
            Thread.sleep(1500) // loadCurrentProfileAndRefresh() runs on an IO coroutine
            onView(withId(R.id.tvProfileBadge)).perform(click())
            Thread.sleep(500)
            onView(withId(R.id.rowAddProfile)).perform(click())
            onView(withId(R.id.etProfileName)).perform(replaceText(newName))
            onView(withText(R.string.save)).perform(click())
            Thread.sleep(1000)
        }

        val current = runBlocking { repository.getCurrentProfile() }
        assertEquals(newName, current?.name)
    }
}
