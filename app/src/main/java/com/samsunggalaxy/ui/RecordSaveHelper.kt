package com.samsunggalaxy.ui

import android.content.Context
import com.samsunggalaxy.data.BmiRecord
import com.samsunggalaxy.data.BmiRepository
import com.samsunggalaxy.utils.AppLog

/**
 * Shared by ResultAct.saveToHistory() (full wizard) and HistoryActivity's quick-log FAB
 * (weight-only entry) — both need the exact same insert+streak+badge flow. Previously each
 * duplicated it independently, risking a fix landing in one and not the other.
 */
object RecordSaveHelper {
    suspend fun saveAndCheckBadges(
        context: Context,
        repository: BmiRepository,
        record: BmiRecord,
        goalWeight: Double?
    ): List<BadgeManager.Badge> {
        val insertedId = repository.insertRecord(record)
        AppLog.d("RecordSaveHelper: inserted record id=$insertedId, profileId=${record.profileId}, weight=${record.weight}, bmi=${record.bmi}")
        StreakManager.recordCheck(context, record.profileId)

        val recordCount = repository.getRecordCount(record.profileId)
        val recentBmiValues = repository.getRecentBmiValues(record.profileId, 7)
        return BadgeManager.checkAll(
            context = context,
            profileId = record.profileId,
            recordCount = recordCount,
            currentBmi = record.bmi,
            currentWeight = record.weight,
            goalWeight = goalWeight,
            recentBmiList = recentBmiValues
        )
    }
}
