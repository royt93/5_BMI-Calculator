package com.samsunggalaxy.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Regression coverage for EPIC-00 T00.4: MainAct used to display a stale streak count
 * (`getStreakData()` returns the raw persisted value) until the user calculated BMI again.
 * `computeDisplayCurrent` is the pure helper that detects an already-broken streak for display.
 */
class StreakManagerLogicTest {

    private val today = LocalDate.of(2026, 8, 23)

    @Test
    fun lastCheckedToday_currentUnchanged() {
        assertEquals(5, StreakManager.computeDisplayCurrent(current = 5, lastDate = "2026-08-23", today = today))
    }

    @Test
    fun lastCheckedYesterday_currentUnchanged_streakStillAlive() {
        assertEquals(5, StreakManager.computeDisplayCurrent(current = 5, lastDate = "2026-08-22", today = today))
    }

    @Test
    fun lastCheckedTwoDaysAgo_streakBroken_displayZero() {
        assertEquals(0, StreakManager.computeDisplayCurrent(current = 5, lastDate = "2026-08-21", today = today))
    }

    @Test
    fun lastCheckedLongAgo_streakBroken_displayZero() {
        assertEquals(0, StreakManager.computeDisplayCurrent(current = 30, lastDate = "2026-01-01", today = today))
    }

    @Test
    fun neverChecked_nullLastDate_currentUnchanged() {
        assertEquals(0, StreakManager.computeDisplayCurrent(current = 0, lastDate = null, today = today))
    }
}
