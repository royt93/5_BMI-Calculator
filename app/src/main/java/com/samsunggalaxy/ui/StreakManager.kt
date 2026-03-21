package com.samsunggalaxy.ui

import android.content.Context
import android.util.Log
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
        Log.d("roy93~", "StreakManager.recordCheck: today=$today, lastDate=$lastDate")

        if (lastDate == today) {
            Log.d("roy93~", "StreakManager.recordCheck: already checked today, skipping")
            return
        }

        val yesterday = LocalDate.now().minusDays(1).toString()
        val current = prefs.getInt(KEY_CURRENT, 0)
        val newStreak = if (lastDate == yesterday) current + 1 else 1
        val best = maxOf(newStreak, prefs.getInt(KEY_BEST, 0))
        Log.d("roy93~", "StreakManager.recordCheck: yesterday=$yesterday, current=$current, newStreak=$newStreak, best=$best")

        val success = prefs.edit()
            .putInt(KEY_CURRENT, newStreak)
            .putInt(KEY_BEST, best)
            .putString(KEY_LAST, today)
            .commit()
        Log.d("roy93~", "StreakManager.recordCheck: commit success=$success")
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

    data class StreakData(val current: Int, val best: Int, val lastDate: String?)
}
