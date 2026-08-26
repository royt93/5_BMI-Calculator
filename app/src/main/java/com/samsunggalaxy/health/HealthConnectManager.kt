package com.samsunggalaxy.health

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Mass
import com.samsunggalaxy.data.BmiRecord
import com.samsunggalaxy.data.BmiRepository
import com.samsunggalaxy.ui.RecordSaveHelper
import com.samsunggalaxy.utils.AppLog
import com.samsunggalaxy.utils.CalculatorUtils
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * EPIC-09 T09.2 — bidirectional weight sync with Health Connect, last-write-wins.
 *
 * connect-client:1.1.0 declares minSdk 26 (this app supports 24 via
 * tools:overrideLibrary in the manifest — see AndroidManifest.xml comment), so every entry
 * point here starts with an SDK_INT gate. Devices below API 26 simply can't have the Health
 * Connect provider installed anyway, so this isn't a real feature loss for them.
 *
 * Sync algorithm (see syncNow): local `timestamp` doubles as "last modified" because the app
 * never edits a BmiRecord's weight in place except via this sync (MainAct/quick-log only ever
 * insert new rows) — so comparing it against Health Connect's `metadata.lastModifiedTime` is a
 * valid last-write-wins comparison. `healthConnectRecordId` links a local row to the Health
 * Connect record it's paired with, both to update-in-place instead of duplicating and to avoid
 * an export/import ping-pong loop.
 */
object HealthConnectManager {
    private val REQUIRED_PERMISSIONS = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class)
    )
    private const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
    private const val SYNC_WINDOW_DAYS = 30L

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    fun isAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE) == HealthConnectClient.SDK_AVAILABLE
    }

    fun installProviderIntentUri(): Uri =
        Uri.parse("https://play.google.com/store/apps/details?id=$PROVIDER_PACKAGE")

    fun requestPermissionsContract() = PermissionController.createRequestPermissionResultContract()

    fun requiredPermissions(): Set<String> = REQUIRED_PERMISSIONS

    private fun getClientOrNull(context: Context): HealthConnectClient? {
        if (!isAvailable(context)) return null
        return HealthConnectClient.getOrCreate(context)
    }

    suspend fun hasAllPermissions(context: Context): Boolean {
        val client = getClientOrNull(context) ?: return false
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(REQUIRED_PERMISSIONS)
    }

    /**
     * EPIC-09 T09.2 — must be called whenever a local record with a non-null
     * `healthConnectRecordId` is deleted (HistoryActivity swipe-delete, Settings "Clear
     * History"). Without this, the next sync finds the still-present Health Connect record with
     * no local counterpart and silently re-imports it as a "new" row, resurrecting data the user
     * just deleted.
     */
    suspend fun deleteRecords(context: Context, healthConnectRecordIds: List<String>) {
        if (healthConnectRecordIds.isEmpty()) return
        if (!isAvailable(context) || !hasAllPermissions(context)) return
        try {
            val client = HealthConnectClient.getOrCreate(context)
            client.deleteRecords(WeightRecord::class, recordIdsList = healthConnectRecordIds, clientRecordIdsList = emptyList())
        } catch (e: Exception) {
            AppLog.w("HealthConnectManager.deleteRecords failed", e)
        }
    }

    sealed class SyncResult {
        data object Unavailable : SyncResult()
        data object MissingPermissions : SyncResult()
        data class Success(val imported: Int, val exported: Int, val updated: Int) : SyncResult()
        data class Failed(val message: String?) : SyncResult()
    }

    /** EPIC-09 T09.2 — last-write-wins bidirectional sync for one profile's weight records. */
    suspend fun syncNow(context: Context, repository: BmiRepository, profileId: Long): SyncResult {
        if (!isAvailable(context)) return SyncResult.Unavailable
        if (!hasAllPermissions(context)) return SyncResult.MissingPermissions
        val client = HealthConnectClient.getOrCreate(context)

        return try {
            val sinceMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(SYNC_WINDOW_DAYS)
            val hcRecords = client.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.after(Instant.ofEpochMilli(sinceMs))
                )
            ).records
            val hcById = hcRecords.associateBy { it.metadata.id }.toMutableMap()

            val localRecords = repository.getRecordsSince(profileId, sinceMs)
            var imported = 0
            var exported = 0
            var updated = 0

            for (local in localRecords) {
                val linkedHcId = local.healthConnectRecordId
                if (linkedHcId != null) {
                    val hc = hcById.remove(linkedHcId)
                    if (hc == null) continue // linked record was deleted on the Health Connect side — out of scope for v1

                    // Comparison uses lastModifiedTime (wall-clock edit recency, the correct
                    // signal for "which side changed more recently"); the value actually stored
                    // uses hc.time (the weigh-in's real timestamp) — see the import branch below
                    // for why conflating the two corrupts the displayed date.
                    val hcModifiedMs = hc.metadata.lastModifiedTime.toEpochMilli()
                    when {
                        hcModifiedMs > local.timestamp -> {
                            val newBmi = CalculatorUtils.calculateBMI(hc.weight.inKilograms, local.height)
                            repository.updateWeightFromSync(local.id, hc.weight.inKilograms, newBmi, hc.time.toEpochMilli())
                            updated++
                        }
                        local.timestamp > hcModifiedMs && local.source == BmiRecord.SOURCE_APP -> {
                            pushToHealthConnect(client, local, existingHealthConnectRecordId = linkedHcId)
                            updated++
                        }
                    }
                } else if (local.source == BmiRecord.SOURCE_APP) {
                    val newHcId = pushToHealthConnect(client, local, existingHealthConnectRecordId = null)
                    repository.linkHealthConnectRecord(local.id, newHcId)
                    exported++
                }
            }

            // Remaining hcById entries have no local counterpart — import as new records, reusing
            // the current profile's latest height/gender/age (same pattern as HistoryActivity's
            // weight-only quick-log FAB, EPIC-07 T07.5).
            val latestKnown = repository.getMostRecentRecord(profileId)
            for (hc in hcById.values) {
                val height = latestKnown?.height ?: continue // no baseline height yet — skip, nothing sane to compute BMI against
                val weightKg = hc.weight.inKilograms
                val bmi = CalculatorUtils.calculateBMI(weightKg, height)
                val record = BmiRecord(
                    // hc.time is the actual weigh-in moment; metadata.lastModifiedTime is when
                    // it was written/synced into Health Connect, which can be hours or days
                    // later (backfilled entries) — using the latter here would show the weigh-in
                    // on the wrong date in history/charts/the widget sparkline.
                    timestamp = hc.time.toEpochMilli(),
                    height = height,
                    weight = weightKg,
                    gender = latestKnown.gender,
                    age = latestKnown.age,
                    bmi = bmi,
                    bmr = latestKnown.bmr,
                    tdee = latestKnown.tdee,
                    idealWeightMin = latestKnown.idealWeightMin,
                    idealWeightMax = latestKnown.idealWeightMax,
                    bodyFatPercentage = null,
                    profileId = profileId,
                    source = BmiRecord.SOURCE_HEALTH_CONNECT,
                    healthConnectRecordId = hc.metadata.id
                )
                RecordSaveHelper.saveAndCheckBadges(context, repository, record, goalWeight = null, triggerHealthConnectSync = false)
                imported++
            }

            SyncResult.Success(imported, exported, updated)
        } catch (e: Exception) {
            AppLog.w("HealthConnectManager.syncNow failed", e)
            SyncResult.Failed(e.message)
        }
    }

    /**
     * Updating an existing Health Connect record must go through `updateRecords()` keyed on its
     * platform-assigned `metadata.id` (= our `healthConnectRecordId`) — NOT another
     * `insertRecords()` call. Health Connect's clientRecordId-based upsert only recognizes the
     * clientRecordId that was used at ORIGINAL insert time; passing the platform id in that slot
     * (an earlier bug here) doesn't match anything, so Health Connect just inserts a duplicate.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun pushToHealthConnect(
        client: HealthConnectClient,
        local: BmiRecord,
        existingHealthConnectRecordId: String?
    ): String {
        val instant = Instant.ofEpochMilli(local.timestamp)
        val zoneOffset = ZoneId.systemDefault().rules.getOffset(instant)
        val weight = Mass.kilograms(local.weight)

        if (existingHealthConnectRecordId != null) {
            val record = WeightRecord(
                time = instant,
                zoneOffset = zoneOffset,
                weight = weight,
                metadata = Metadata.manualEntryWithId(existingHealthConnectRecordId)
            )
            client.updateRecords(listOf(record))
            return existingHealthConnectRecordId
        }

        val record = WeightRecord(
            time = instant,
            zoneOffset = zoneOffset,
            weight = weight,
            metadata = Metadata.manualEntry()
        )
        val response = client.insertRecords(listOf(record))
        return response.recordIdsList.first()
    }
}
