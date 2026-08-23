package com.samsunggalaxy.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface BmiDao {
    @Insert
    suspend fun insert(record: BmiRecord): Long

    @Delete
    suspend fun delete(record: BmiRecord)

    @Query("SELECT * FROM bmi_records WHERE profileId = :profileId ORDER BY timestamp DESC")
    fun getAllRecords(profileId: Long): LiveData<List<BmiRecord>>

    @Query("SELECT * FROM bmi_records WHERE profileId = :profileId ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentRecords(profileId: Long, limit: Int): LiveData<List<BmiRecord>>

    @Query("DELETE FROM bmi_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM bmi_records WHERE profileId = :profileId ORDER BY timestamp ASC")
    fun getAllRecordsAscending(profileId: Long): LiveData<List<BmiRecord>>

    @Query("SELECT COUNT(*) FROM bmi_records WHERE profileId = :profileId")
    suspend fun getRecordCount(profileId: Long): Int

    @Query("SELECT bmi FROM bmi_records WHERE profileId = :profileId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentBmiValues(profileId: Long, limit: Int): List<Double>

    @Query("SELECT weight FROM bmi_records WHERE profileId = :profileId ORDER BY timestamp ASC LIMIT 1")
    suspend fun getFirstRecordWeight(profileId: Long): Double?

    @Query("SELECT * FROM bmi_records WHERE profileId = :profileId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getMostRecentRecord(profileId: Long): BmiRecord?

    // No @ForeignKey cascade is defined between bmi_records.profileId and profiles.id,
    // so deleting a profile must explicitly clear its records first (EPIC-05 T05.2).
    @Query("DELETE FROM bmi_records WHERE profileId = :profileId")
    suspend fun deleteAllByProfile(profileId: Long)
}
