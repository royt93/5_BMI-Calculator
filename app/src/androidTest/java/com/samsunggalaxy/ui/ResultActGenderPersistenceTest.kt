package com.samsunggalaxy.ui

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.samsunggalaxy.data.AppDatabase
import com.samsunggalaxy.data.BmiRepository
import com.samsunggalaxy.data.Profile
import com.samsunggalaxy.utils.CalculatorUtils
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end integration test: MainAct's intent extras -> ResultAct.saveToHistory() ->
 * Room persistence, for the "Other" gender regression (EPIC-00 T00.5).
 *
 * Full UI-driven navigation through MainAct's custom wheel-picker widgets is exercised
 * manually/instrumented separately (they don't expose stable Espresso ids); this test
 * targets the same regression at the Intent+DB boundary, which is where the bug actually
 * lived (MainAct mapping 'O' -> Gender extra, ResultAct persisting the BMR it computed
 * from that extra) — a reliable, non-flaky way to cover the full write path.
 *
 * Isolation: ResultAct always saves into the real "current profile" — this test runs
 * against a throwaway Profile (torn down in @After) so it never pollutes real user data.
 *
 * Needs a connected device/emulator: ./gradlew connectedDevDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class ResultActGenderPersistenceTest {

    private lateinit var repository: BmiRepository
    private var profileId: Long = -1L
    private var originalProfileId: Long = 1L

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = AppDatabase.getDatabase(context)
        repository = BmiRepository(db.bmiDao(), db.profileDao())
        originalProfileId = repository.getCurrentProfile()?.id ?: 1L
        profileId = repository.insertProfile(Profile(name = "GenderTestProfile_${System.nanoTime()}", isCurrent = false))
        repository.setCurrentProfile(profileId)
    }

    @After
    fun tearDown() = runBlocking {
        repository.setCurrentProfile(originalProfileId)
        repository.deleteProfile(Profile(id = profileId, name = "cleanup", isCurrent = false))
    }

    @Test
    fun otherGender_savedRecord_usesNeutralFormula_notFemale() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val expectedFemaleBmr = CalculatorUtils.calculateBMR(70.0, 175.0, 30, genderCode = 1)
        val expectedNeutralBmr = CalculatorUtils.calculateBMR(70.0, 175.0, 30, genderCode = 2)

        val intent = Intent(context, ResultAct::class.java).apply {
            putExtra("Weight", 70.0)
            putExtra("Height", 175.0)
            putExtra("Age", 30)
            putExtra("Gender", 2) // "Other" — MainAct now maps 'O' here instead of defaulting to 1 (Female)
        }
        ActivityScenario.launch<ResultAct>(intent).use {
            Thread.sleep(1500) // saveToHistory() runs on an IO coroutine; no IdlingResource wired yet.
        }

        val latestRecord = runBlocking { repository.getMostRecentRecord(profileId) }
        assertEquals(2, latestRecord?.gender) // sanity: the record we just triggered, not a stale one
        val latestBmr = latestRecord!!.bmr

        assertNotEquals(
            "Gender=2 (Other) must not be persisted using the Female BMR formula",
            expectedFemaleBmr,
            latestBmr,
            0.001
        )
        assertEquals(expectedNeutralBmr, latestBmr, 0.001)
    }
}
