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
    val profileId: Long = 0 // For multi-profile support
)
