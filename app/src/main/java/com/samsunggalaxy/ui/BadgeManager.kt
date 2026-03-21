package com.samsunggalaxy.ui

import android.content.Context
import com.samsunggalaxy.R

object BadgeManager {
    private const val PREFS = "badge_prefs"

    enum class Badge(val id: String, val titleRes: Int, val descRes: Int, val iconRes: Int) {
        FIRST_STEP("first_step", R.string.badge_first_step, R.string.badge_first_step_desc, R.drawable.ic_badge_star),
        WEEK_WARRIOR("week_warrior", R.string.badge_week_warrior, R.string.badge_week_warrior_desc, R.drawable.ic_badge_fire),
        MONTHLY_MASTER("monthly_master", R.string.badge_monthly_master, R.string.badge_monthly_master_desc, R.drawable.ic_badge_calendar),
        GOAL_CRUSHER("goal_crusher", R.string.badge_goal_crusher, R.string.badge_goal_crusher_desc, R.drawable.ic_badge_target),
        HEALTHY_ZONE("healthy_zone", R.string.badge_healthy_zone, R.string.badge_healthy_zone_desc, R.drawable.ic_badge_heart),
        DATA_LOVER("data_lover", R.string.badge_data_lover, R.string.badge_data_lover_desc, R.drawable.ic_badge_chart);
    }

    fun isEarned(context: Context, badge: Badge): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("${badge.id}_earned", false)
    }

    fun getEarnedDate(context: Context, badge: Badge): Long {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong("${badge.id}_date", 0L)
    }

    fun getEarnedCount(context: Context): Int {
        return Badge.values().count { isEarned(context, it) }
    }

    /**
     * Check all badge conditions. Returns list of NEWLY earned badges (for celebration).
     */
    fun checkAll(
        context: Context,
        recordCount: Int,
        currentBmi: Double,
        currentWeight: Double,
        goalWeight: Double?,
        recentBmiList: List<Double>
    ): List<Badge> {
        val newlyEarned = mutableListOf<Badge>()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun tryUnlock(badge: Badge, condition: Boolean) {
            if (!prefs.getBoolean("${badge.id}_earned", false) && condition) {
                prefs.edit()
                    .putBoolean("${badge.id}_earned", true)
                    .putLong("${badge.id}_date", System.currentTimeMillis())
                    .apply()
                newlyEarned.add(badge)
            }
        }

        tryUnlock(Badge.FIRST_STEP, recordCount >= 1)
        tryUnlock(Badge.DATA_LOVER, recordCount >= 50)

        val streakData = StreakManager.getStreakData(context)
        tryUnlock(Badge.WEEK_WARRIOR, streakData.best >= 7)
        tryUnlock(Badge.MONTHLY_MASTER, streakData.best >= 30)

        if (goalWeight != null && goalWeight > 0) {
            // Goal reached if within 1kg of goal (handles both lose and gain goals)
            val reachedGoal = kotlin.math.abs(currentWeight - goalWeight) <= 1.0
            tryUnlock(Badge.GOAL_CRUSHER, reachedGoal)
        }

        if (recentBmiList.size >= 7) {
            val allHealthy = recentBmiList.takeLast(7).all { it in 18.5..25.0 }
            tryUnlock(Badge.HEALTHY_ZONE, allHealthy)
        }

        return newlyEarned
    }
}
