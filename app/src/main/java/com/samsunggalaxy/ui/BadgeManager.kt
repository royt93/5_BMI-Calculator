package com.samsunggalaxy.ui

import android.content.Context
import com.samsunggalaxy.R

/**
 * Badges are scoped per profile (SharedPrefs file `badge_prefs_<profileId>`) so family
 * members tracked via separate profiles (EPIC-05) each earn their own badges instead of
 * sharing one global set. `LEGACY_PREFS` is the single pre-multi-profile file —
 * [migrateLegacyIfNeeded] copies it into whichever profile reads first after upgrading,
 * then clears it so it's a one-time move.
 */
object BadgeManager {
    private const val LEGACY_PREFS = "badge_prefs"

    private fun prefsName(profileId: Long) = "badge_prefs_$profileId"

    enum class Badge(val id: String, val titleRes: Int, val descRes: Int, val iconRes: Int) {
        FIRST_STEP("first_step", R.string.badge_first_step, R.string.badge_first_step_desc, R.drawable.ic_badge_star),
        WEEK_WARRIOR("week_warrior", R.string.badge_week_warrior, R.string.badge_week_warrior_desc, R.drawable.ic_badge_fire),
        MONTHLY_MASTER("monthly_master", R.string.badge_monthly_master, R.string.badge_monthly_master_desc, R.drawable.ic_badge_calendar),
        GOAL_CRUSHER("goal_crusher", R.string.badge_goal_crusher, R.string.badge_goal_crusher_desc, R.drawable.ic_badge_target),
        HEALTHY_ZONE("healthy_zone", R.string.badge_healthy_zone, R.string.badge_healthy_zone_desc, R.drawable.ic_badge_heart),
        DATA_LOVER("data_lover", R.string.badge_data_lover, R.string.badge_data_lover_desc, R.drawable.ic_badge_chart),
        MEASURE_TAKER("measure_taker", R.string.badge_measure_taker, R.string.badge_measure_taker_desc, R.drawable.ic_badge_ruler),
        DATA_EXPORTER("data_exporter", R.string.badge_data_exporter, R.string.badge_data_exporter_desc, R.drawable.ic_badge_export);
    }

    private fun migrateLegacyIfNeeded(context: Context, profileId: Long) {
        val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val hasLegacyData = Badge.values().any { legacy.contains("${it.id}_earned") }
        if (!hasLegacyData) return

        val scoped = context.getSharedPreferences(prefsName(profileId), Context.MODE_PRIVATE)
        val alreadyMigrated = Badge.values().any { scoped.contains("${it.id}_earned") }
        if (alreadyMigrated) return

        val editor = scoped.edit()
        Badge.values().forEach { b ->
            if (legacy.contains("${b.id}_earned")) {
                editor.putBoolean("${b.id}_earned", legacy.getBoolean("${b.id}_earned", false))
                editor.putLong("${b.id}_date", legacy.getLong("${b.id}_date", 0L))
            }
        }
        editor.apply()
        legacy.edit().clear().apply() // consume — only the first profile to ask gets it
    }

    /** Called when a profile is deleted (EPIC-05 T05.2) so its SharedPrefs file isn't orphaned. */
    fun clearProfileData(context: Context, profileId: Long) {
        context.deleteSharedPreferences(prefsName(profileId))
    }

    fun isEarned(context: Context, profileId: Long, badge: Badge): Boolean {
        migrateLegacyIfNeeded(context, profileId)
        return context.getSharedPreferences(prefsName(profileId), Context.MODE_PRIVATE)
            .getBoolean("${badge.id}_earned", false)
    }

    fun getEarnedDate(context: Context, profileId: Long, badge: Badge): Long {
        migrateLegacyIfNeeded(context, profileId)
        return context.getSharedPreferences(prefsName(profileId), Context.MODE_PRIVATE)
            .getLong("${badge.id}_date", 0L)
    }

    fun getEarnedCount(context: Context, profileId: Long): Int {
        return Badge.values().count { isEarned(context, profileId, it) }
    }

    /**
     * Check all badge conditions. Returns list of NEWLY earned badges (for celebration).
     */
    fun checkAll(
        context: Context,
        profileId: Long,
        recordCount: Int,
        currentBmi: Double,
        currentWeight: Double,
        goalWeight: Double?,
        recentBmiList: List<Double>
    ): List<Badge> {
        val newlyEarned = mutableListOf<Badge>()

        fun tryUnlock(badge: Badge, condition: Boolean) {
            if (condition) tryUnlockSingle(context, profileId, badge)?.let { newlyEarned.add(it) }
        }

        tryUnlock(Badge.FIRST_STEP, recordCount >= 1)
        tryUnlock(Badge.DATA_LOVER, recordCount >= 50)

        val streakData = StreakManager.getStreakData(context, profileId)
        tryUnlock(Badge.WEEK_WARRIOR, streakData.best >= 7)
        tryUnlock(Badge.MONTHLY_MASTER, streakData.best >= 30)

        if (goalWeight != null && goalWeight > 0) {
            // Shared tolerance with CalculatorUtils.calculateGoalProgress — keeps the goal
            // card/dashboard "achieved" state and this badge from silently drifting apart.
            val reachedGoal = kotlin.math.abs(currentWeight - goalWeight) <= com.samsunggalaxy.utils.CalculatorUtils.GOAL_ACHIEVED_TOLERANCE_KG
            tryUnlock(Badge.GOAL_CRUSHER, reachedGoal)
        }

        if (recentBmiList.size >= 7) {
            val allHealthy = recentBmiList.takeLast(7).all { it in 18.5..25.0 }
            tryUnlock(Badge.HEALTHY_ZONE, allHealthy)
        }

        return newlyEarned
    }

    private fun tryUnlockSingle(context: Context, profileId: Long, badge: Badge): Badge? {
        migrateLegacyIfNeeded(context, profileId)
        val prefs = context.getSharedPreferences(prefsName(profileId), Context.MODE_PRIVATE)
        if (prefs.getBoolean("${badge.id}_earned", false)) return null
        prefs.edit()
            .putBoolean("${badge.id}_earned", true)
            .putLong("${badge.id}_date", System.currentTimeMillis())
            .apply()
        return badge
    }

    /** EPIC-08 T08.3 — call after the first BodyMeasurement is saved for this profile. */
    fun tryUnlockMeasureTaker(context: Context, profileId: Long): Badge? =
        tryUnlockSingle(context, profileId, Badge.MEASURE_TAKER)

    /** EPIC-08 T08.2 — call after the first successful history export for this profile. */
    fun tryUnlockDataExporter(context: Context, profileId: Long): Badge? =
        tryUnlockSingle(context, profileId, Badge.DATA_EXPORTER)
}
