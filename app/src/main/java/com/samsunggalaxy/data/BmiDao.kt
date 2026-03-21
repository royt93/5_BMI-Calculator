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
}
