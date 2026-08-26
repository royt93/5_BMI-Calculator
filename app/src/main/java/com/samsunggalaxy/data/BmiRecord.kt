package com.samsunggalaxy.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bmi_records")
data class BmiRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val height: Double,
    val weight: Double,
    val gender: Int, // 0 = Male, 1 = Female
    val age: Int,
    val bmi: Double,
    val bmr: Double,
    val tdee: Double,
    val idealWeightMin: Double,
    val idealWeightMax: Double,
    val bodyFatPercentage: Double?,
    val profileId: Long = 0, // For multi-profile support
    // EPIC-09 T09.2 — Health Connect bidirectional sync. "APP" = created in-app (default,
    // covers every pre-existing row after migration). "HEALTH_CONNECT" = imported from a
    // WeightRecord written by another app. healthConnectRecordId links this row to the
    // Health Connect record it's paired with (null = never synced) so the sync worker can
    // tell an update from a brand-new record and avoid re-importing/re-exporting in a loop.
    val source: String = SOURCE_APP,
    val healthConnectRecordId: String? = null
) {
    companion object {
        const val SOURCE_APP = "APP"
        const val SOURCE_HEALTH_CONNECT = "HEALTH_CONNECT"
    }
}
