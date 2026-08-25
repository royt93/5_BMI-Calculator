package com.samsunggalaxy.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface BodyMeasurementDao {
    @Insert
    suspend fun insert(measurement: BodyMeasurement): Long

    @Query("SELECT * FROM body_measurements WHERE profileId = :profileId ORDER BY timestamp ASC")
    fun getAllAscending(profileId: Long): LiveData<List<BodyMeasurement>>

    @Query("SELECT COUNT(*) FROM body_measurements WHERE profileId = :profileId")
    suspend fun getCount(profileId: Long): Int

    // No @ForeignKey cascade between body_measurements.profileId and profiles.id (matches
    // BmiDao's bmi_records — see EPIC-05 T05.2), so profile deletion must clear this explicitly.
    @Query("DELETE FROM body_measurements WHERE profileId = :profileId")
    suspend fun deleteAllByProfile(profileId: Long)
}
