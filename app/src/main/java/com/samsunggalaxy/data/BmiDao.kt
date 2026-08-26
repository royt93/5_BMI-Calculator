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

    // EPIC-06 T06.2 — the standalone Body Fat calculator computes a value from measurements
    // not tied to a weigh-in; this attaches it onto an existing record (today's) rather than
    // fabricating a whole new BmiRecord with a made-up weight/BMI. Returns rows affected so the
    // caller can detect the record having been deleted concurrently (0 = nothing was updated).
    @Query("UPDATE bmi_records SET bodyFatPercentage = :value WHERE id = :recordId")
    suspend fun updateBodyFatPercentage(recordId: Long, value: Double): Int

    // EPIC-09 T09.1 — widget 7-day sparkline needs a bounded time window, not a row LIMIT
    // (a LIMIT-based query would include entries older than 7 days if the user hasn't
    // logged every day).
    @Query("SELECT * FROM bmi_records WHERE profileId = :profileId AND timestamp >= :sinceTimestampMs ORDER BY timestamp ASC")
    suspend fun getRecordsSince(profileId: Long, sinceTimestampMs: Long): List<BmiRecord>

    // Idea I1 — Progress Photo Timeline. Attaches a photo onto an already-saved record, same
    // narrow-update pattern as updateBodyFatPercentage — the photo is picked/captured after the
    // automatic save already inserted the row. Returns rows affected (0 = record was deleted
    // concurrently).
    @Query("UPDATE bmi_records SET photoPath = :photoPath WHERE id = :recordId")
    suspend fun updatePhotoPath(recordId: Long, photoPath: String?): Int

    @Query("SELECT * FROM bmi_records WHERE profileId = :profileId AND photoPath IS NOT NULL ORDER BY timestamp DESC")
    suspend fun getRecordsWithPhotos(profileId: Long): List<BmiRecord>

    // EPIC-09 T09.2 — Health Connect sync: find the local row already linked to a given
    // Health Connect record so an update can be applied in place instead of duplicated.
    @Query("SELECT * FROM bmi_records WHERE healthConnectRecordId = :healthConnectRecordId LIMIT 1")
    suspend fun getRecordByHealthConnectId(healthConnectRecordId: String): BmiRecord?

    @Query("SELECT * FROM bmi_records WHERE profileId = :profileId AND source = :source ORDER BY timestamp ASC")
    suspend fun getRecordsBySource(profileId: Long, source: String): List<BmiRecord>

    @Query("UPDATE bmi_records SET healthConnectRecordId = :healthConnectRecordId WHERE id = :recordId")
    suspend fun linkHealthConnectRecord(recordId: Long, healthConnectRecordId: String): Int

    @Query("UPDATE bmi_records SET weight = :weight, bmi = :bmi, timestamp = :timestamp WHERE id = :recordId")
    suspend fun updateWeightFromSync(recordId: Long, weight: Double, bmi: Double, timestamp: Long): Int

    // EPIC-09 T09.2 — collected before a bulk delete (Settings "Clear History") so the linked
    // Health Connect records can be removed too; otherwise the next sync re-imports them.
    @Query("SELECT healthConnectRecordId FROM bmi_records WHERE profileId = :profileId AND healthConnectRecordId IS NOT NULL")
    suspend fun getHealthConnectRecordIds(profileId: Long): List<String>
}
