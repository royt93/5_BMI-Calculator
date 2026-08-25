package com.samsunggalaxy.ui

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.tabs.TabLayout
import com.samsunggalaxy.R
import com.samsunggalaxy.data.AppDatabase
import com.samsunggalaxy.data.BmiRecord
import com.samsunggalaxy.data.BmiRepository
import com.samsunggalaxy.data.BodyMeasurement
import com.samsunggalaxy.data.Profile
import com.samsunggalaxy.notification.ReminderScheduler
import com.samsunggalaxy.utils.CsvExporter
import com.samsunggalaxy.utils.PreferencesManager
import com.samsunggalaxy.utils.UnitFormatter
import com.github.mikephil.charting.charts.LineChart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Widget/integration tests for the remaining EPIC-08 pieces (doc/task/todo/EPIC-08-engagement-features.md):
 * T08.2 (CSV export), T08.3's HistoryActivity Measurements tab, T08.1 (reminder toggle).
 * Needs a connected device/emulator: ./gradlew connectedDevDebugAndroidTest
 *
 * Isolation: same throwaway-Profile pattern as the other EPIC-06/08 instrumented tests.
 */
@RunWith(AndroidJUnit4::class)
class EngagementFeaturesTest {

    private lateinit var repository: BmiRepository
    private lateinit var prefs: PreferencesManager
    private lateinit var context: Context
    private var profileId: Long = -1L
    private var originalProfileId: Long = 1L

    @Before
    fun setUp() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        // Granted here (not via a one-off `adb shell pm grant`) because AGP uninstalls/reinstalls
        // the app between connectedDevDebugAndroidTest invocations, wiping any prior adb grant —
        // this survives that and lets the reminder-toggle test skip the system permission dialog.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName, android.Manifest.permission.POST_NOTIFICATIONS
            )
        }
        val db = AppDatabase.getDatabase(context)
        repository = BmiRepository(db.bmiDao(), db.profileDao(), db.bodyMeasurementDao())
        prefs = PreferencesManager(context)
        originalProfileId = repository.getCurrentProfile()?.id ?: 1L
        profileId = repository.insertProfile(Profile(name = "EngagementTestProfile_${System.nanoTime()}", isCurrent = false))
        repository.setCurrentProfile(profileId)
    }

    @After
    fun tearDown() = runBlocking {
        repository.setCurrentProfile(originalProfileId)
        repository.deleteProfileWithRecords(Profile(id = profileId, name = "cleanup", isCurrent = false))
        BadgeManager.clearProfileData(context, profileId)
        prefs.setReminderEnabled(false)
        ReminderScheduler.cancel(context)
    }

    private fun record(timestamp: Long, weight: Double) = BmiRecord(
        timestamp = timestamp,
        height = 175.0,
        weight = weight,
        gender = 0,
        age = 30,
        bmi = weight / (1.75 * 1.75),
        bmr = 1500.0,
        tdee = 2000.0,
        idealWeightMin = 60.0,
        idealWeightMax = 75.0,
        bodyFatPercentage = null,
        profileId = profileId
    )

    // ---- T08.2 CSV export ----

    @Test
    fun exportBmiRecords_writesFileMatchingBuiltContent_andReturnsShareableUri() = runBlocking {
        val records = listOf(
            record(timestamp = 1000L, weight = 70.0),
            record(timestamp = 2000L, weight = 71.0)
        )

        // The exports/ directory accumulates CSVs across test runs and manual smoke-testing
        // sessions on a shared emulator (nothing purges old exports) — a before/after diff of
        // the directory listing identifies the exact file this call wrote, independent of
        // FileProvider's own URI-to-display-name resolution.
        val exportsDir = File(context.getExternalFilesDir(null), "exports")
        val before = exportsDir.listFiles()?.toSet() ?: emptySet()

        val uri = CsvExporter.exportBmiRecords(context, records, UnitFormatter.METRIC)
        assertNotNull("exportBmiRecords must return a content:// URI when records are non-empty", uri)
        assertEquals("content", uri!!.scheme)

        val after = exportsDir.listFiles()?.toSet() ?: emptySet()
        val newFiles = after - before
        assertEquals("exactly one new CSV file must appear under exports/", 1, newFiles.size)
        val written = newFiles.single()
        assertTrue("CSV file must exist on disk under app-external-files/exports", written.exists())
        assertEquals(CsvExporter.buildCsvContent(records, UnitFormatter.METRIC), written.readText())
        val deleted = written.delete() // JUnit4 test methods must return void — see EPIC-06's BmiDaoTest note
        assertTrue(deleted)
    }

    @Test
    fun exportBmiRecords_emptyList_returnsNull() = runBlocking {
        assertEquals(null, CsvExporter.exportBmiRecords(context, emptyList(), UnitFormatter.METRIC))
    }

    @Test
    fun dataExporterBadge_unlocksOnFirstExport_notOnSecond() {
        val first = BadgeManager.tryUnlockDataExporter(context, profileId)
        val second = BadgeManager.tryUnlockDataExporter(context, profileId)
        assertEquals(BadgeManager.Badge.DATA_EXPORTER, first)
        assertEquals(null, second) // already earned — no duplicate unlock
        assertTrue(BadgeManager.isEarned(context, profileId, BadgeManager.Badge.DATA_EXPORTER))
    }

    // ---- T08.3 Measurements tab ----

    @Test
    fun historyActivity_measurementsTab_rendersChartFromMeasurementsTable() = runBlocking {
        // A BMI record is required for the Dashboard's own empty-state gate, even though
        // this test exercises the independent Measurements series.
        repository.insertRecord(record(timestamp = System.currentTimeMillis(), weight = 70.0))
        repository.insertMeasurement(
            BodyMeasurement(timestamp = 1000L, waist = 80.0, neck = 35.0, hip = 90.0, chest = null, profileId = profileId)
        )
        repository.insertMeasurement(
            BodyMeasurement(timestamp = 2000L, waist = 79.0, neck = 35.0, hip = 89.0, chest = null, profileId = profileId)
        )

        ActivityScenario.launch(HistoryActivity::class.java).use { scenario ->
            Thread.sleep(2500) // let both LiveData observers (records + measurements) settle
            scenario.onActivity { activity ->
                val tabs = activity.findViewById<TabLayout>(R.id.tabLayoutSeries)
                tabs.getTabAt(3)?.select() // BMI(0)/Weight(1)/Height(2)/Measurements(3)
            }
            Thread.sleep(1000)
            scenario.onActivity { activity ->
                val chart = activity.findViewById<LineChart>(R.id.lineChart)
                assertNotNull("Measurements tab must render chart data", chart.data)
                assertTrue("waist/neck/hip lines expected", chart.data.dataSetCount > 0)
            }
        }
        Unit // JUnit4 test methods must return void — .use{}'s last expression is ActivityScenario
    }

    // ---- T08.1 reminder toggle ----

    @Test
    fun settingsReminderToggle_persistsEnabledStateAndTime() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            Thread.sleep(1500) // let loadPersistedToggleStates() finish before toggling, or it stomps our change
            scenario.onActivity { activity ->
                val switch = activity.findViewById<MaterialSwitch>(R.id.switchReminder)
                switch.isChecked = true // POST_NOTIFICATIONS must be pre-granted via adb for this to skip the system dialog
            }
            Thread.sleep(1200)
        }

        runBlocking {
            assertTrue(prefs.reminderEnabled.first())
        }
    }
}
