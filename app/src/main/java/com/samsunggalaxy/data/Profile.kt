package com.samsunggalaxy.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val isCurrent: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
