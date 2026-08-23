package com.samsunggalaxy.ui

import android.content.Intent
import android.widget.ProgressBar
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.samsunggalaxy.R
import com.samsunggalaxy.data.AppDatabase
import com.samsunggalaxy.data.BmiRepository
import com.samsunggalaxy.data.Profile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Widget/integration test for ResultAct's goal card + BMI headline — exercises EPIC-00
 * T00.1 (goal-weight gain-direction bug) and T00.3 (BMI category consistency) against the
 * real inflated views, not just the pure CalculatorUtils logic already covered by
 * CalculatorUtilsTest (src/test). Needs a connected device/emulator:
 * ./gradlew connectedDevDebugAndroidTest
 *
 * Note: goal-card load + insight/save run on an IO coroutine with no IdlingResource wired
 * up yet — this uses a pragmatic Thread.sleep rather than flake on a timing assumption.
 *
 * Isolation: ResultAct.onCreate() always saves a real BmiRecord via saveToHistory() —
 * launching it against the real "current profile" would permanently pollute the app's
 * actual DB with test records. Each test runs against its own throwaway Profile instead
 * (fresh autoIncrement id, torn down in @After), so nothing here touches real user data.
 */
@RunWith(AndroidJUnit4::class)
class ResultActGoalCardTest {

    private lateinit var repository: BmiRepository
    private var profileId: Long = -1L
    private var originalProfileId: Long = 1L

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = AppDatabase.getDatabase(context)
        repository = BmiRepository(db.bmiDao(), db.profileDao())
        originalProfileId = repository.getCurrentProfile()?.id ?: 1L
        profileId = repository.insertProfile(Profile(name = "GoalCardTestProfile_${System.nanoTime()}", isCurrent = false))
        repository.setCurrentProfile(profileId)
        // Gain-goal scenario: target (80kg) is ABOVE the weight ResultAct will be launched with (70kg).
        repository.updateGoalWeight(profileId, 80.0)
    }

    @After
    fun tearDown() = runBlocking {
        repository.setCurrentProfile(originalProfileId)
        repository.deleteProfile(Profile(id = profileId, name = "cleanup", isCurrent = false))
    }

    @Test
    fun gainGoal_notFalselyMarkedAchievedOnFreshLaunch() {
        // Regression: `diff = weight - goalWeight <= 0` used to mark ANY gain goal
        // (goalWeight > current weight) as 100% "Achieved" immediately on first save.
        val intent = Intent(InstrumentationRegistry.getInstrumentation().targetContext, ResultAct::class.java).apply {
            putExtra("Weight", 70.0)
            putExtra("Height", 175.0)
            putExtra("Age", 30)
            putExtra("Gender", 0)
        }
        ActivityScenario.launch<ResultAct>(intent).use { scenario ->
            Thread.sleep(1500)
            scenario.onActivity { activity ->
                val progressBar = activity.findViewById<ProgressBar>(R.id.progressGoal)
                val tvRemaining = activity.findViewById<TextView>(R.id.tvGoalRemaining)
                assertTrue(
                    "gain goal must not show 100% progress on a fresh launch (was: ${progressBar.progress})",
                    progressBar.progress < 100
                )
                assertNotEquals(
                    activity.getString(R.string.goal_weight_achieved),
                    tvRemaining.text.toString()
                )
            }
        }
    }

    @Test
    fun bmiHeadline_matchesHealthyCategory_atBmi24point9() {
        // Regression: showResult() used 18.5/24.9/30 thresholds (BMI 24.9 => "Overweight")
        // while setupHealthTips()/showGoalDialog() used 18.5/25.0/30 (BMI 24.9 => "Healthy")
        // — same screen, same BMI, two different English category strings.
        // weight=76.23kg / height=175cm ~= BMI 24.9
        val intent = Intent(InstrumentationRegistry.getInstrumentation().targetContext, ResultAct::class.java).apply {
            putExtra("Weight", 76.23)
            putExtra("Height", 175.0)
            putExtra("Age", 30)
            putExtra("Gender", 0)
        }
        ActivityScenario.launch<ResultAct>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val tvBmi = activity.findViewById<TextView>(R.id.tvBmi)
                assertEquals(activity.getString(R.string.bmi_category_healthy), tvBmi.text.toString())
            }
        }
    }
}
