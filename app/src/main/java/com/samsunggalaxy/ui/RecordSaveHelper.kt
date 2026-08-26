package com.samsunggalaxy.ui

import android.content.Context
import com.samsunggalaxy.data.BmiRecord
import com.samsunggalaxy.data.BmiRepository
import com.samsunggalaxy.health.HealthConnectSyncScheduler
import com.samsunggalaxy.utils.AppLog
import com.samsunggalaxy.utils.PreferencesManager
import com.samsunggalaxy.widget.WidgetUpdateHelper
import kotlinx.coroutines.flow.first

/**
 * Shared by ResultAct.saveToHistory() (full wizard) and HistoryActivity's quick-log FAB
 * (weight-only entry) — both need the exact same insert+streak+badge flow. Previously each
 * duplicated it independently, risking a fix landing in one and not the other.
 */
object RecordSaveHelper {
    /**
     * @param triggerHealthConnectSync Set false only when called FROM HealthConnectManager's own
     * import path (a record just pulled from Health Connect) — otherwise every import would
     * enqueue another sync pass that immediately re-scans and finds nothing new, wasted work
     * rather than a real bug, but worth avoiding.
     */
    suspend fun saveAndCheckBadges(
        context: Context,
        repository: BmiRepository,
        record: BmiRecord,
        goalWeight: Double?,
        triggerHealthConnectSync: Boolean = true
    ): List<BadgeManager.Badge> {
        val insertedId = repository.insertRecord(record)
        AppLog.d("RecordSaveHelper: inserted record id=$insertedId, profileId=${record.profileId}, weight=${record.weight}, bmi=${record.bmi}")
        StreakManager.recordCheck(context, record.profileId)
        WidgetUpdateHelper.updateAllWidgets(context)

        // Fire-and-forget via WorkManager, not awaited inline — Health Connect I/O (up to 30
        // days of records, several DB round-trips) has no bearing on whether the save itself
        // succeeded and shouldn't delay the save confirmation the user sees.
        if (triggerHealthConnectSync && PreferencesManager(context).healthConnectSyncEnabled.first()) {
            HealthConnectSyncScheduler.enqueueOneTimeSync(context)
        }

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
