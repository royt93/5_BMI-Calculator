package com.samsunggalaxy.ui

import android.content.Context
import android.util.Log
import com.samsunggalaxy.BuildConfig
import com.samsunggalaxy.data.BmiRecord
import com.samsunggalaxy.data.BmiRepository

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
        if (BuildConfig.DEBUG) Log.d("roy93~", "RecordSaveHelper: inserted record id=$insertedId, profileId=${record.profileId}, weight=${record.weight}, bmi=${record.bmi}")
        StreakManager.recordCheck(context)

        val recordCount = repository.getRecordCount(record.profileId)
        val recentBmiValues = repository.getRecentBmiValues(record.profileId, 7)
        return BadgeManager.checkAll(
            context = context,
            recordCount = recordCount,
            currentBmi = record.bmi,
            currentWeight = record.weight,
            goalWeight = goalWeight,
            recentBmiList = recentBmiValues
        )
    }
}
