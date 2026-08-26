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

    // ---- EPIC-09 T09.1/T09.2 — widget sparkline window + Health Connect sync linkage ----

    @Test
    fun getRecordsSince_excludesRecordsOlderThanCutoff() = runBlocking {
        dao.insert(record(timestamp = 1000L, weight = 68.0)) // before cutoff — excluded
        dao.insert(record(timestamp = 5000L, weight = 70.0))
        dao.insert(record(timestamp = 6000L, weight = 71.0))

        val recent = dao.getRecordsSince(1L, sinceTimestampMs = 5000L)

        assertEquals(2, recent.size)
        assertEquals(70.0, recent[0].weight, 0.001) // ASC order
        assertEquals(71.0, recent[1].weight, 0.001)
    }

    @Test
    fun linkHealthConnectRecord_thenGetRecordByHealthConnectId_findsIt() = runBlocking {
        val id = dao.insert(record(timestamp = 1000L, weight = 70.0))

        dao.linkHealthConnectRecord(id, "hc-abc-123")

        val found = dao.getRecordByHealthConnectId("hc-abc-123")
        assertEquals(id, found!!.id)
    }

    @Test
    fun getRecordByHealthConnectId_unknownId_returnsNull() = runBlocking {
        assertNull(dao.getRecordByHealthConnectId("does-not-exist"))
    }

    @Test
    fun getRecordsBySource_filtersCorrectly() = runBlocking {
        dao.insert(record(timestamp = 1000L, weight = 70.0).copy(source = BmiRecord.SOURCE_APP))
        dao.insert(record(timestamp = 2000L, weight = 71.0).copy(source = BmiRecord.SOURCE_HEALTH_CONNECT))

        val appRecords = dao.getRecordsBySource(1L, BmiRecord.SOURCE_APP)
        val hcRecords = dao.getRecordsBySource(1L, BmiRecord.SOURCE_HEALTH_CONNECT)

        assertEquals(1, appRecords.size)
        assertEquals(70.0, appRecords[0].weight, 0.001)
        assertEquals(1, hcRecords.size)
        assertEquals(71.0, hcRecords[0].weight, 0.001)
    }

    @Test
    fun updateWeightFromSync_updatesInPlace_doesNotInsertNewRecord() = runBlocking {
        val id = dao.insert(record(timestamp = 1000L, weight = 70.0))

        val rowsUpdated = dao.updateWeightFromSync(id, weight = 72.5, bmi = 23.7, timestamp = 2000L)

        assertEquals(1, rowsUpdated)
        assertEquals(1, dao.getRecordCount(1L))
        val updated = dao.getMostRecentRecord(1L)
        assertEquals(72.5, updated!!.weight, 0.001)
        assertEquals(23.7, updated.bmi, 0.001)
        assertEquals(2000L, updated.timestamp)
    }

    @Test
    fun newRecord_defaultsToAppSource_withNoHealthConnectLink() = runBlocking {
        val id = dao.insert(record(timestamp = 1000L, weight = 70.0))

        val inserted = dao.getRecordByHealthConnectId("nonexistent") // sanity: unrelated lookup returns null
        assertNull(inserted)

        val stored = dao.getMostRecentRecord(1L)
        assertEquals(id, stored!!.id)
        assertEquals(BmiRecord.SOURCE_APP, stored.source)
        assertNull(stored.healthConnectRecordId)
    }

    // ---- Idea I1 — Progress Photo Timeline ----

    @Test
    fun updatePhotoPath_attachesPhotoOntoExistingRecord() = runBlocking {
        val id = dao.insert(record(timestamp = 1000L, weight = 70.0))

        val rowsAffected = dao.updatePhotoPath(id, "/data/photos/photo_1.jpg")

        assertEquals(1, rowsAffected)
        assertEquals("/data/photos/photo_1.jpg", dao.getMostRecentRecord(1L)!!.photoPath)
    }

    @Test
    fun updatePhotoPath_deletedRecord_returnsZeroRowsAffected() = runBlocking {
        assertEquals(0, dao.updatePhotoPath(recordId = 999L, photoPath = "/data/photos/photo_1.jpg"))
    }

    @Test
    fun updatePhotoPath_nullClearsAnExistingPhoto() = runBlocking {
        val id = dao.insert(record(timestamp = 1000L, weight = 70.0))
        dao.updatePhotoPath(id, "/data/photos/photo_1.jpg")

        dao.updatePhotoPath(id, null)

        assertNull(dao.getMostRecentRecord(1L)!!.photoPath)
    }

    @Test
    fun getRecordsWithPhotos_onlyReturnsRecordsThatHaveAPhoto_mostRecentFirst() = runBlocking {
        val withoutPhoto = dao.insert(record(timestamp = 1000L, weight = 70.0))
        val olderWithPhoto = dao.insert(record(timestamp = 2000L, weight = 69.0))
        val newerWithPhoto = dao.insert(record(timestamp = 3000L, weight = 68.0))
        dao.updatePhotoPath(olderWithPhoto, "/data/photos/older.jpg")
        dao.updatePhotoPath(newerWithPhoto, "/data/photos/newer.jpg")

        val withPhotos = dao.getRecordsWithPhotos(1L)

        assertEquals(2, withPhotos.size)
        assertEquals(listOf(newerWithPhoto, olderWithPhoto), withPhotos.map { it.id })
        assertEquals(true, withPhotos.none { it.id == withoutPhoto })
    }

    @Test
    fun getRecordsWithPhotos_isScopedPerProfile() = runBlocking {
        val ownRecord = dao.insert(record(timestamp = 1000L, weight = 70.0, profileId = 1L))
        val otherProfileRecord = dao.insert(record(timestamp = 1000L, weight = 60.0, profileId = 2L))
        dao.updatePhotoPath(ownRecord, "/data/photos/mine.jpg")
        dao.updatePhotoPath(otherProfileRecord, "/data/photos/other.jpg")

        val withPhotos = dao.getRecordsWithPhotos(1L)

        assertEquals(listOf(ownRecord), withPhotos.map { it.id })
    }
}
