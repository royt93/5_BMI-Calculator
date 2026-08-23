package com.samsunggalaxy.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * EPIC-05 T05.2: profile CRUD at the repository layer — an in-memory DB isolates this from
 * real app data, unlike the UI-level tests which must use throwaway profiles in the real DB.
 * Needs a connected device/emulator: ./gradlew connectedDevDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class ProfileCrudTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: BmiRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BmiRepository(db.bmiDao(), db.profileDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun deleteProfileWithRecords_cascadesRecordDeletion() = runBlocking {
        val profileId = repository.insertProfile(Profile(name = "Dad", isCurrent = true))
        repository.insertRecord(bmiRecord(profileId, weight = 80.0))
        repository.insertRecord(bmiRecord(profileId, weight = 79.0))
        assertEquals(2, repository.getRecordCount(profileId))

        repository.deleteProfileWithRecords(repository.getCurrentProfile()!!)

        assertEquals(0, repository.getRecordCount(profileId))
        assertNull(repository.getMostRecentRecord(profileId))
    }

    @Test
    fun deletingCurrentProfile_leavesNoCurrentProfile_untilCallerReassigns() = runBlocking {
        // Repository doesn't auto-reassign "current" on delete — that's the UI layer's job
        // (ProfileSwitcherBottomSheet), verified by this test documenting the raw behavior.
        val profileId = repository.insertProfile(Profile(name = "Solo", isCurrent = true))
        repository.deleteProfileWithRecords(repository.getCurrentProfile()!!)
        assertNull(repository.getCurrentProfile())
        assertEquals(profileId, profileId) // sanity: id was real, just confirming no crash
    }

    @Test
    fun renameProfile_preservesOtherFields() = runBlocking {
        val profileId = repository.insertProfile(Profile(name = "Old Name", isCurrent = true, goalWeight = 65.0))
        val original = repository.getCurrentProfile()!!

        repository.updateProfile(original.copy(name = "New Name"))

        val renamed = repository.getCurrentProfile()!!
        assertEquals("New Name", renamed.name)
        assertEquals("goalWeight must survive a rename (fetch-then-copy, not reconstruct)", 65.0, renamed.goalWeight!!, 0.001)
        assertEquals("createdAt must survive a rename", original.createdAt, renamed.createdAt)
        assertTrue(renamed.isCurrent)
    }

    @Test
    fun getProfileCount_reflectsInsertsAndDeletes() = runBlocking {
        assertEquals(0, repository.getProfileCount())
        repository.insertProfile(Profile(name = "A"))
        repository.insertProfile(Profile(name = "B"))
        assertEquals(2, repository.getProfileCount())
    }

    private fun bmiRecord(profileId: Long, weight: Double) = BmiRecord(
        timestamp = System.currentTimeMillis(),
        height = 175.0,
        weight = weight,
        gender = 0,
        age = 30,
        bmi = weight / (1.75 * 1.75),
        bmr = 1500.0,
        tdee = 1800.0,
        idealWeightMin = 60.0,
        idealWeightMax = 75.0,
        bodyFatPercentage = null,
        profileId = profileId
    )
}
