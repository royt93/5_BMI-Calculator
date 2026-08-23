package com.samsunggalaxy.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration test (Room, in-memory DB) for the goal-progress baseline query added in
 * EPIC-00 T00.1 (BmiDao.getFirstRecordWeight) and the badge/streak support queries it sits
 * alongside. Runs on-device — see doc/task/todo/EPIC-00-critical-bugs.md.
 */
@RunWith(AndroidJUnit4::class)
class BmiDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: BmiDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.bmiDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun record(timestamp: Long, weight: Double, profileId: Long = 1L) = BmiRecord(
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

    @Test
    fun getFirstRecordWeight_returnsEarliestByTimestamp_notInsertionOrder() = runBlocking {
        // Insert out of chronological order to prove the query sorts by timestamp, not id.
        dao.insert(record(timestamp = 3000L, weight = 68.0))
        dao.insert(record(timestamp = 1000L, weight = 80.0)) // earliest in time, inserted 2nd
        dao.insert(record(timestamp = 2000L, weight = 74.0))

        assertEquals(80.0, dao.getFirstRecordWeight(1L)!!, 0.001)
    }

    @Test
    fun getFirstRecordWeight_noRecords_returnsNull() = runBlocking {
        assertNull(dao.getFirstRecordWeight(1L))
    }

    @Test
    fun getFirstRecordWeight_isScopedPerProfile() = runBlocking {
        dao.insert(record(timestamp = 1000L, weight = 80.0, profileId = 1L))
        dao.insert(record(timestamp = 500L, weight = 55.0, profileId = 2L))

        assertEquals(80.0, dao.getFirstRecordWeight(1L)!!, 0.001)
        assertEquals(55.0, dao.getFirstRecordWeight(2L)!!, 0.001)
    }

    @Test
    fun getRecordCount_and_getRecentBmiValues_matchInsertedData() = runBlocking {
        dao.insert(record(timestamp = 1000L, weight = 70.0))
        dao.insert(record(timestamp = 2000L, weight = 71.0))
        dao.insert(record(timestamp = 3000L, weight = 72.0))

        assertEquals(3, dao.getRecordCount(1L))
        val recent = dao.getRecentBmiValues(1L, limit = 2)
        assertEquals(2, recent.size)
        // Most recent first (ORDER BY timestamp DESC)
        assertEquals(record(3000L, 72.0).bmi, recent[0], 0.001)
    }

    // ---- updateBodyFatPercentage (EPIC-06 T06.2) ----

    @Test
    fun updateBodyFatPercentage_attachesValueWithoutInsertingNewRecord() = runBlocking {
        dao.insert(record(timestamp = 1000L, weight = 70.0))
        val id2 = dao.insert(record(timestamp = 2000L, weight = 71.0))

        val rowsUpdated = dao.updateBodyFatPercentage(id2, 18.5)

        assertEquals(1, rowsUpdated)
        assertEquals(2, dao.getRecordCount(1L)) // UPDATE, not a fabricated 3rd record
        val mostRecent = dao.getMostRecentRecord(1L)
        assertEquals(id2, mostRecent!!.id)
        assertEquals(18.5, mostRecent.bodyFatPercentage!!, 0.001)
    }

    @Test
    fun updateBodyFatPercentage_unknownRecordId_returnsZeroRowsAffected() = runBlocking {
        assertEquals(0, dao.updateBodyFatPercentage(recordId = 999L, value = 20.0))
    }
}
