package com.samsunggalaxy.data

import androidx.lifecycle.LiveData

class BmiRepository(
    private val bmiDao: BmiDao,
    private val profileDao: ProfileDao,
    private val bodyMeasurementDao: BodyMeasurementDao
) {

    fun getAllRecords(profileId: Long): LiveData<List<BmiRecord>> {
        return bmiDao.getAllRecords(profileId)
    }

    fun getRecentRecords(profileId: Long, limit: Int = 10): LiveData<List<BmiRecord>> {
        return bmiDao.getRecentRecords(profileId, limit)
    }

    fun getAllRecordsAscending(profileId: Long): LiveData<List<BmiRecord>> {
        return bmiDao.getAllRecordsAscending(profileId)
    }

    suspend fun insertRecord(record: BmiRecord): Long {
        return bmiDao.insert(record)
    }

    suspend fun deleteRecord(record: BmiRecord) {
        bmiDao.delete(record)
    }

    suspend fun deleteRecordById(id: Long) {
        bmiDao.deleteById(id)
    }

    // Profile methods
    fun getAllProfiles(): LiveData<List<Profile>> {
        return profileDao.getAllProfiles()
    }

    suspend fun getAllProfilesOnce(): List<Profile> {
        return profileDao.getAllProfilesOnce()
    }

    suspend fun getCurrentProfile(): Profile? {
        return profileDao.getCurrentProfile()
    }

    suspend fun insertProfile(profile: Profile): Long {
        return profileDao.insert(profile)
    }

    suspend fun updateProfile(profile: Profile) {
        profileDao.update(profile)
    }

    suspend fun deleteProfile(profile: Profile) {
        profileDao.delete(profile)
    }

    /** Deletes the profile AND every BmiRecord/BodyMeasurement tied to it (no DB-level cascade — see BmiDao). */
    suspend fun deleteProfileWithRecords(profile: Profile) {
        bmiDao.deleteAllByProfile(profile.id)
        bodyMeasurementDao.deleteAllByProfile(profile.id)
        profileDao.delete(profile)
    }

    suspend fun getProfileCount(): Int {
        return profileDao.getProfileCount()
    }

    suspend fun setCurrentProfile(profileId: Long) {
        profileDao.setCurrentProfileAtomic(profileId)
    }

    suspend fun createDefaultProfile(): Long {
        val defaultProfile = Profile(name = "Default", isCurrent = true)
        return profileDao.insert(defaultProfile)
    }

    // Goal Weight Feature
    suspend fun updateGoalWeight(profileId: Long, goalWeight: Double?) {
        profileDao.updateGoalWeight(profileId, goalWeight)
    }

    // Badge Feature
    suspend fun getRecordCount(profileId: Long): Int {
        return bmiDao.getRecordCount(profileId)
    }

    suspend fun getRecentBmiValues(profileId: Long, limit: Int): List<Double> {
        return bmiDao.getRecentBmiValues(profileId, limit)
    }

    // Goal Weight Feature — baseline for direction-aware progress (see CalculatorUtils.calculateGoalProgress)
    suspend fun getFirstRecordWeight(profileId: Long): Double? {
        return bmiDao.getFirstRecordWeight(profileId)
    }

    suspend fun getMostRecentRecord(profileId: Long): BmiRecord? {
        return bmiDao.getMostRecentRecord(profileId)
    }

    /** EPIC-04 T04.4 — Settings "Clear History": wipes records but keeps the profile itself. */
    suspend fun clearHistory(profileId: Long) {
        bmiDao.deleteAllByProfile(profileId)
        bodyMeasurementDao.deleteAllByProfile(profileId)
    }

    /** EPIC-06 T06.2 — attach a Body Fat calculator result onto an existing record. Returns rows affected. */
    suspend fun updateBodyFatPercentage(recordId: Long, value: Double): Int {
        return bmiDao.updateBodyFatPercentage(recordId, value)
    }

    /** EPIC-06 T06.1 — shared prefill lookup used by all 4 standalone calculators. */
    suspend fun getCurrentProfileMostRecentRecord(): BmiRecord? {
        val profile = getCurrentProfile() ?: return null
        return getMostRecentRecord(profile.id)
    }

    // EPIC-08 T08.3 — body measurements (waist/neck/hip/chest) tracked over time
    suspend fun insertMeasurement(measurement: BodyMeasurement): Long {
        return bodyMeasurementDao.insert(measurement)
    }

    fun getMeasurementsAscending(profileId: Long): LiveData<List<BodyMeasurement>> {
        return bodyMeasurementDao.getAllAscending(profileId)
    }

    suspend fun getMeasurementCount(profileId: Long): Int {
        return bodyMeasurementDao.getCount(profileId)
    }
}
