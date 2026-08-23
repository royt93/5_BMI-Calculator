package com.samsunggalaxy.ui

import android.content.Context
import android.util.Log
import com.samsunggalaxy.BuildConfig
import java.time.LocalDate

object StreakManager {
    private const val PREFS = "streak_prefs"
    private const val KEY_CURRENT = "current_streak"
    private const val KEY_BEST = "best_streak"
    private const val KEY_LAST = "last_check_date"

    fun recordCheck(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        val lastDate = prefs.getString(KEY_LAST, null)
        if (BuildConfig.DEBUG) Log.d("roy93~", "StreakManager.recordCheck: today=$today, lastDate=$lastDate")

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

    fun getStreakData(context: Context): StreakData {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return StreakData(
            current = prefs.getInt(KEY_CURRENT, 0),
            best = prefs.getInt(KEY_BEST, 0),
            lastDate = prefs.getString(KEY_LAST, null)
        )
    }

    fun isTodayChecked(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST, null) == LocalDate.now().toString()
    }

    /**
     * `getStreakData()` returns the raw persisted value, which stays stale (shows the old
     * streak count) until the user calculates BMI again and `recordCheck()` runs. Use this
     * for UI display instead — it detects an already-broken streak (missed >1 day) even
     * before the user takes any new action.
     */
    fun getDisplayStreak(context: Context): StreakData {
        val raw = getStreakData(context)
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
