package com.samsunggalaxy.data

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Integration test (Room, in-memory DB) for EPIC-08 T08.3's new body_measurements table —
 * see doc/task/todo/EPIC-08-engagement-features.md.
 */
@RunWith(AndroidJUnit4::class)
class BodyMeasurementDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: BodyMeasurementDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.bodyMeasurementDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun measurement(timestamp: Long, waist: Double?, profileId: Long = 1L) = BodyMeasurement(
        timestamp = timestamp,
        waist = waist,
        neck = 35.0,
        hip = 90.0,
        chest = null,
        profileId = profileId
    )

    private fun <T> observeOnce(liveData: LiveData<T>): T {
        val latch = CountDownLatch(1)
        var result: T? = null
        val observer = object : Observer<T> {
            override fun onChanged(value: T) {
                result = value
                latch.countDown()
                Handler(Looper.getMainLooper()).post { liveData.removeObserver(this) }
            }
        }
        Handler(Looper.getMainLooper()).post { liveData.observeForever(observer) }
        latch.await(5, TimeUnit.SECONDS)
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    @Test
    fun insertAndReadBack_ascendingByTimestamp_regardlessOfInsertionOrder() = runBlocking {
        dao.insert(measurement(timestamp = 3000L, waist = 82.0))
        dao.insert(measurement(timestamp = 1000L, waist = 80.0))
        dao.insert(measurement(timestamp = 2000L, waist = 81.0))

        val result = observeOnce(dao.getAllAscending(1L))
        assertEquals(listOf(80.0, 81.0, 82.0), result.map { it.waist })
    }

    @Test
    fun getCount_isScopedPerProfile() = runBlocking {
        dao.insert(measurement(timestamp = 1000L, waist = 80.0, profileId = 1L))
        dao.insert(measurement(timestamp = 1000L, waist = 60.0, profileId = 2L))
        dao.insert(measurement(timestamp = 2000L, waist = 61.0, profileId = 2L))

        assertEquals(1, dao.getCount(1L))
        assertEquals(2, dao.getCount(2L))
    }

    @Test
    fun deleteAllByProfile_onlyRemovesThatProfilesRows() = runBlocking {
        dao.insert(measurement(timestamp = 1000L, waist = 80.0, profileId = 1L))
        dao.insert(measurement(timestamp = 1000L, waist = 60.0, profileId = 2L))

        dao.deleteAllByProfile(1L)

        assertEquals(0, dao.getCount(1L))
        assertEquals(1, dao.getCount(2L))
    }

    @Test
    fun nullableFields_roundTripWithoutCrashing() = runBlocking {
        dao.insert(BodyMeasurement(timestamp = 1000L, waist = 80.0, neck = null, hip = null, chest = 95.0, profileId = 1L))
        val result = observeOnce(dao.getAllAscending(1L))
        assertEquals(1, result.size)
        assertEquals(95.0, result[0].chest)
        assertEquals(null, result[0].neck)
    }
}
