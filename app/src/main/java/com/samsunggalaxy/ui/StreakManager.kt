package com.samsunggalaxy.ui

import android.content.Context
import android.util.Log
import com.samsunggalaxy.BuildConfig
import java.time.LocalDate

/**
 * Streak is scoped per profile (SharedPrefs file `streak_prefs_<profileId>`) so family
 * members tracked via separate profiles (EPIC-05) don't share/clobber each other's streak.
 * `LEGACY_PREFS` is the single pre-multi-profile file — [migrateLegacyIfNeeded] copies it
 * into whichever profile reads first after upgrading, then clears it so it's a one-time move.
 */
object StreakManager {
    private const val LEGACY_PREFS = "streak_prefs"
    private const val KEY_CURRENT = "current_streak"
    private const val KEY_BEST = "best_streak"
    private const val KEY_LAST = "last_check_date"

    private fun prefsName(profileId: Long) = "streak_prefs_$profileId"

    private fun migrateLegacyIfNeeded(context: Context, profileId: Long) {
        val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        if (!legacy.contains(KEY_LAST)) return // nothing to migrate

        val scoped = context.getSharedPreferences(prefsName(profileId), Context.MODE_PRIVATE)
        if (scoped.contains(KEY_LAST)) return // this profile already has its own data

        scoped.edit()
            .putInt(KEY_CURRENT, legacy.getInt(KEY_CURRENT, 0))
            .putInt(KEY_BEST, legacy.getInt(KEY_BEST, 0))
            .putString(KEY_LAST, legacy.getString(KEY_LAST, null))
            .apply()
        legacy.edit().clear().apply() // consume — only the first profile to ask gets it
    }

    fun recordCheck(context: Context, profileId: Long) {
        migrateLegacyIfNeeded(context, profileId)
        val prefs = context.getSharedPreferences(prefsName(profileId), Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        val lastDate = prefs.getString(KEY_LAST, null)
        if (BuildConfig.DEBUG) Log.d("roy93~", "StreakManager.recordCheck: profileId=$profileId, today=$today, lastDate=$lastDate")

        if (lastDate == today) {
            if (BuildConfig.DEBUG) Log.d("roy93~", "StreakManager.recordCheck: already checked today, skipping")
            return
        }

        val yesterday = LocalDate.now().minusDays(1).toString()
        val current = prefs.getInt(KEY_CURRENT, 0)
        val newStreak = if (lastDate == yesterday) current + 1 else 1
        val best = maxOf(newStreak, prefs.getInt(KEY_BEST, 0))
        if (BuildConfig.DEBUG) Log.d("roy93~", "StreakManager.recordCheck: yesterday=$yesterday, current=$current, newStreak=$newStreak, best=$best")

        val success = prefs.edit()
            .putInt(KEY_CURRENT, newStreak)
            .putInt(KEY_BEST, best)
            .putString(KEY_LAST, today)
            .commit()
        if (BuildConfig.DEBUG) Log.d("roy93~", "StreakManager.recordCheck: commit success=$success")
    }

    fun getStreakData(context: Context, profileId: Long): StreakData {
        migrateLegacyIfNeeded(context, profileId)
        val prefs = context.getSharedPreferences(prefsName(profileId), Context.MODE_PRIVATE)
        return StreakData(
            current = prefs.getInt(KEY_CURRENT, 0),
            best = prefs.getInt(KEY_BEST, 0),
            lastDate = prefs.getString(KEY_LAST, null)
        )
    }

    /** Called when a profile is deleted (EPIC-05 T05.2) so its SharedPrefs file isn't orphaned. */
    fun clearProfileData(context: Context, profileId: Long) {
        context.deleteSharedPreferences(prefsName(profileId))
    }

    fun isTodayChecked(context: Context, profileId: Long): Boolean {
        migrateLegacyIfNeeded(context, profileId)
        val prefs = context.getSharedPreferences(prefsName(profileId), Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST, null) == LocalDate.now().toString()
    }

    /**
     * `getStreakData()` returns the raw persisted value, which stays stale (shows the old
     * streak count) until the user calculates BMI again and `recordCheck()` runs. Use this
     * for UI display instead — it detects an already-broken streak (missed >1 day) even
     * before the user takes any new action.
     */
    fun getDisplayStreak(context: Context, profileId: Long): StreakData {
        val raw = getStreakData(context, profileId)
        val displayCurrent = computeDisplayCurrent(raw.current, raw.lastDate)
        return if (displayCurrent == raw.current) raw else raw.copy(current = displayCurrent)
    }

    /** Pure logic (no Context) so it's directly unit-testable. */
    fun computeDisplayCurrent(current: Int, lastDate: String?, today: LocalDate = LocalDate.now()): Int {
        if (lastDate == null) return current
        val yesterday = today.minusDays(1).toString()
        return if (lastDate == today.toString() || lastDate == yesterday) current else 0
    }

    data class StreakData(val current: Int, val best: Int, val lastDate: String?)
}
