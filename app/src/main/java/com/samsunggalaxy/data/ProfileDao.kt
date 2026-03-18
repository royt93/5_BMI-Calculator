package com.samsunggalaxy.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface ProfileDao {
    @Insert
    suspend fun insert(profile: Profile): Long

    @Update
    suspend fun update(profile: Profile)

    @Delete
    suspend fun delete(profile: Profile)

    @Query("SELECT * FROM profiles ORDER BY createdAt DESC")
    fun getAllProfiles(): LiveData<List<Profile>>

    @Query("SELECT * FROM profiles WHERE isCurrent = 1 LIMIT 1")
    suspend fun getCurrentProfile(): Profile?

    @Query("UPDATE profiles SET isCurrent = 0")
    suspend fun clearAllCurrent()

    @Query("UPDATE profiles SET isCurrent = 1 WHERE id = :profileId")
    suspend fun setCurrentProfile(profileId: Long)

    @Transaction
    suspend fun setCurrentProfileAtomic(profileId: Long) {
        clearAllCurrent()
        setCurrentProfile(profileId)
    }

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE profiles SET goalWeight = :goalWeight WHERE id = :profileId")
    suspend fun updateGoalWeight(profileId: Long, goalWeight: Double?)
}
