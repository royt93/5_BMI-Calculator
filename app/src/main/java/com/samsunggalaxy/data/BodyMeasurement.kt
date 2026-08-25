package com.samsunggalaxy.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** EPIC-08 T08.3 — waist/neck/hip/chest tracked over time, independent of a BMI weigh-in. */
@Entity(tableName = "body_measurements")
data class BodyMeasurement(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val waist: Double?,
    val neck: Double?,
    val hip: Double?,
    val chest: Double?,
    val profileId: Long = 0
)
