package com.samsunggalaxy.ui

import android.content.Intent
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.samsunggalaxy.R
import com.samsunggalaxy.data.AppDatabase
import com.samsunggalaxy.data.BmiRecord
import com.samsunggalaxy.data.BmiRepository
import com.samsunggalaxy.data.Profile
import com.samsunggalaxy.utils.PreferencesManager
import com.samsunggalaxy.utils.UnitFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Widget/integration tests for EPIC-06 (doc/task/todo/EPIC-06-calculator-hub-integration.md):
 * T06.1 (prefill from latest record), T06.2 (Body Fat save-to-history), T06.3 (ResultAct
 * cross-links). Needs a connected device/emulator: ./gradlew connectedDevDebugAndroidTest
 *
 * Isolation: same pattern as ResultActGoalCardTest — a throwaway Profile per test, torn
 * down in @After, so nothing here touches the real "current profile" data.
 */
@RunWith(AndroidJUnit4::class)
class CalculatorHubIntegrationTest {

    private lateinit var repository: BmiRepository
    private lateinit var prefs: PreferencesManager
    private var profileId: Long = -1L
    private var originalProfileId: Long = 1L
    private var originalUnitSystem: String = UnitFormatter.METRIC

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = AppDatabase.getDatabase(context)
        repository = BmiRepository(db.bmiDao(), db.profileDao())
        prefs = PreferencesManager(context)
        // Assertions below expect metric-unit strings — pin it regardless of ambient state
        // left over from other tests (e.g. DashboardUnitSystemTest).
        originalUnitSystem = prefs.unitSystem.first()
        prefs.setUnitSystem(UnitFormatter.METRIC)
        originalProfileId = repository.getCurrentProfile()?.id ?: 1L
        profileId = repository.insertProfile(Profile(name = "CalcHubTestProfile_${System.nanoTime()}", isCurrent = false))
        repository.setCurrentProfile(profileId)
        // JUnit4 test lifecycle methods must return void — capture the id so the block's
        // (and thus setUp()'s inferred) type is Unit, not the Long insertRecord() returns.
        val recordId = repository.insertRecord(
            BmiRecord(
                timestamp = System.currentTimeMillis(),
                height = 180.0,
                weight = 82.0,
                gender = 1, // female — distinguishable from each Activity's rbMale default
                age = 34,
                bmi = 82.0 / (1.8 * 1.8),
                bmr = 1600.0,
                tdee = 2200.0,
                idealWeightMin = 60.0,
                idealWeightMax = 75.0,
                bodyFatPercentage = null,
                profileId = profileId
            )
        )
    }

    @After
    fun tearDown() = runBlocking {
        repository.setCurrentProfile(originalProfileId)
        repository.deleteProfileWithRecords(Profile(id = profileId, name = "cleanup", isCurrent = false))
        prefs.setUnitSystem(originalUnitSystem)
    }

    @Test
    fun bmrCalculator_prefillsFromLatestRecord() {
        ActivityScenario.launch(BmrCalculatorActivity::class.java).use { scenario ->
            Thread.sleep(1000)
            scenario.onActivity { activity ->
                assertEquals("82.0", activity.findViewById<EditText>(R.id.etWeight).text.toString())
                assertEquals("180.0", activity.findViewById<EditText>(R.id.etHeight).text.toString())
                assertEquals("34", activity.findViewById<EditText>(R.id.etAge).text.toString())
                assertEquals(R.id.rbFemale, activity.findViewById<RadioGroup>(R.id.rgGender).checkedRadioButtonId)
            }
        }
    }

    @Test
    fun tdeeCalculator_prefillsFromLatestRecord() {
        ActivityScenario.launch(TdeeCalculatorActivity::class.java).use { scenario ->
            Thread.sleep(1000)
            scenario.onActivity { activity ->
                assertEquals("82.0", activity.findViewById<EditText>(R.id.etWeight).text.toString())
                assertEquals("180.0", activity.findViewById<EditText>(R.id.etHeight).text.toString())
                assertEquals("34", activity.findViewById<EditText>(R.id.etAge).text.toString())
            }
        }
    }

    @Test
    fun idealWeightCalculator_prefillsHeightAndGender() {
        ActivityScenario.launch(IdealWeightCalculatorActivity::class.java).use { scenario ->
            Thread.sleep(1000)
            scenario.onActivity { activity ->
                assertEquals("180.0", activity.findViewById<EditText>(R.id.etHeight).text.toString())
                assertEquals(R.id.rbFemale, activity.findViewById<RadioGroup>(R.id.rgGender).checkedRadioButtonId)
            }
        }
    }

    @Test
    fun bodyFatCalculator_prefillsHeight_andSavesResultOntoTodayRecord() {
        ActivityScenario.launch(BodyFatCalculatorActivity::class.java).use { scenario ->
            Thread.sleep(1000)
            scenario.onActivity { activity ->
                assertEquals("180.0", activity.findViewById<EditText>(R.id.etHeight).text.toString())

                activity.findViewById<EditText>(R.id.etWaist).setText("80")
                activity.findViewById<EditText>(R.id.etNeck).setText("35")
                activity.findViewById<EditText>(R.id.etHip).setText("95")
                activity.findViewById<Button>(R.id.btnCalculate).performClick()
            }
            Thread.sleep(500)
            scenario.onActivity { activity ->
                val btnSave = activity.findViewById<Button>(R.id.btnSaveToHistory)
                assertEquals(
                    "today's record exists (created in setUp) — Save button must appear",
                    android.view.View.VISIBLE,
                    btnSave.visibility
                )
                btnSave.performClick()
            }
            Thread.sleep(500)
        }

        runBlocking {
            val record = repository.getMostRecentRecord(profileId)
            assertNotNull(record)
            assertTrue("body fat % must be saved onto today's record", record!!.bodyFatPercentage != null && record.bodyFatPercentage!! > 0)
        }
    }

    @Test
    fun resultAct_rowBmr_navigatesToBmrCalculator() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val monitor = InstrumentationRegistry.getInstrumentation()
            .addMonitor(BmrCalculatorActivity::class.java.name, null, false)

        val intent = Intent(context, ResultAct::class.java).apply {
            putExtra("Weight", 70.0)
            putExtra("Height", 175.0)
            putExtra("Age", 30)
            putExtra("Gender", 0)
        }
        ActivityScenario.launch<ResultAct>(intent).use { scenario ->
            Thread.sleep(1500) // let the IO coroutine populate rowBmr's click listener
            scenario.onActivity { activity ->
                activity.findViewById<android.view.View>(R.id.rowBmr).performClick()
            }
            val started = monitor.waitForActivityWithTimeout(3000)
            assertNotNull("tapping the BMR insight row must open BmrCalculatorActivity", started)
        }
        InstrumentationRegistry.getInstrumentation().removeMonitor(monitor)
    }
}
