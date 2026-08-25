package com.samsunggalaxy.ui

import androidx.recyclerview.widget.RecyclerView
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * EPIC-04 T04.1: HistoryActivity's record list respects the persisted unit system.
 * Needs a connected device/emulator: ./gradlew connectedDevDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class DashboardUnitSystemTest {

    private lateinit var prefs: PreferencesManager
    private lateinit var repository: BmiRepository
    private var testProfileId: Long = -1L
    private var originalProfileId: Long = 1L

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        prefs = PreferencesManager(context)
        val db = AppDatabase.getDatabase(context)
        repository = BmiRepository(db.bmiDao(), db.profileDao(), db.bodyMeasurementDao())
        originalProfileId = runBlocking { repository.getCurrentProfile()?.id ?: 1L }
        runBlocking {
            testProfileId = repository.insertProfile(Profile(name = "UnitDashboardTest_${System.nanoTime()}"))
            repository.setCurrentProfile(testProfileId)
            db.bmiDao().insert(
                BmiRecord(
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
                    profileId = testProfileId
                )
            )
        }
    }

    @After
    fun tearDown() = runBlocking {
        repository.setCurrentProfile(originalProfileId)
        repository.deleteProfileWithRecords(Profile(id = testProfileId, name = "cleanup", isCurrent = false))
        prefs.setUnitSystem(UnitFormatter.METRIC)
    }

    @Test
    fun imperialUnitSystem_listItemShowsLbsAndIn_notKgCm() {
        runBlocking { prefs.setUnitSystem(UnitFormatter.IMPERIAL) }

        ActivityScenario.launch(HistoryActivity::class.java).use { scenario ->
            Thread.sleep(1000)
            scenario.onActivity { activity ->
                val rv = activity.findViewById<RecyclerView>(R.id.recyclerViewHistory)
                val holder = rv.findViewHolderForAdapterPosition(0) as HistoryAdapter.ViewHolder
                val detailsText = holder.itemView.findViewById<android.widget.TextView>(R.id.tvDetails).text.toString()
                assertTrue("expected lbs in '$detailsText'", detailsText.contains("lbs"))
                assertTrue("expected in (inches) in '$detailsText'", detailsText.contains(" in"))
                assertTrue("must not show metric kg once imperial is selected: '$detailsText'", !detailsText.contains("kg"))
            }
        }
    }

    @Test
    fun metricUnitSystem_listItemShowsKgCm() {
        runBlocking { prefs.setUnitSystem(UnitFormatter.METRIC) }

        ActivityScenario.launch(HistoryActivity::class.java).use { scenario ->
            Thread.sleep(1000)
            scenario.onActivity { activity ->
                val rv = activity.findViewById<RecyclerView>(R.id.recyclerViewHistory)
                val holder = rv.findViewHolderForAdapterPosition(0) as HistoryAdapter.ViewHolder
                val detailsText = holder.itemView.findViewById<android.widget.TextView>(R.id.tvDetails).text.toString()
                assertTrue("expected kg in '$detailsText'", detailsText.contains("kg"))
                assertTrue("expected cm in '$detailsText'", detailsText.contains("cm"))
            }
        }
    }
}
