package com.samsunggalaxy.utils

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InsightsEngineTest {
    // 2026-08-24 is a Monday — used as a stable anchor so week-bucketing math is easy to verify by hand.
    private val mondayMs = LocalDate.of(2026, 8, 24).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val dayMs = 86_400_000L

    private fun day(offset: Int, weight: Double) = (mondayMs + offset * dayMs) to weight

    // ---- computeWeeklyChanges / findBestWeek ----

    @Test
    fun weeklyChanges_singleWeek_allSevenDaysLogged_deltaIsLastMinusFirst() {
        val records = (0..6).map { day(it, 80.0 - it * 0.2) } // 80.0 -> 78.8
        val weeks = InsightsEngine.computeWeeklyChanges(records)
        assertEquals(1, weeks.size)
        assertEquals(-1.2, weeks[0].deltaKg, 0.001)
        assertEquals(7, weeks[0].daysLogged)
    }

    @Test
    fun weeklyChanges_splitsAcrossWeekBoundary() {
        // offsets 0-6 = week 1 (Mon-Sun), offset 7 = the following Monday = week 2.
        val records = listOf(day(0, 80.0), day(6, 79.0), day(7, 78.5))
        val weeks = InsightsEngine.computeWeeklyChanges(records)
        assertEquals(2, weeks.size)
        assertEquals(2, weeks[0].daysLogged)
        assertEquals(1, weeks[1].daysLogged)
    }

    @Test
    fun weeklyChanges_singleRecordInWeek_deltaIsZero() {
        val weeks = InsightsEngine.computeWeeklyChanges(listOf(day(0, 80.0)))
        assertEquals(0.0, weeks[0].deltaKg, 0.001)
        assertEquals(1, weeks[0].daysLogged)
    }

    @Test
    fun findBestWeek_picksMostNegativeDelta_ignoresSingleRecordWeeks() {
        val records = listOf(
            day(0, 80.0), day(1, 79.0), // week 1: -1.0
            day(7, 79.0), day(8, 76.5), // week 2: -2.5 (best)
            day(14, 76.5) // week 3: single record, excluded
        )
        val weeks = InsightsEngine.computeWeeklyChanges(records)
        val best = InsightsEngine.findBestWeek(weeks)
        assertEquals(-2.5, best!!.deltaKg, 0.001)
    }

    @Test
    fun findBestWeek_noQualifyingWeeks_returnsNull() {
        val weeks = InsightsEngine.computeWeeklyChanges(listOf(day(0, 80.0)))
        assertNull(InsightsEngine.findBestWeek(weeks))
    }

    // ---- findMostStableDayOfWeek ----

    @Test
    fun mostStableDay_picksLowestStdDev() {
        // Monday weights barely move (80.0, 80.1); Wednesday swings a lot (80.0, 85.0).
        val records = listOf(
            day(0, 80.0), day(7, 80.1), // Monday x2
            day(2, 80.0), day(9, 85.0) // Wednesday x2
        )
        assertEquals(DayOfWeek.MONDAY, InsightsEngine.findMostStableDayOfWeek(records))
    }

    @Test
    fun mostStableDay_fewerThanTwoQualifyingWeekdays_returnsNull() {
        // Only Monday has >=2 samples; Wednesday has just 1 — not enough weekdays to compare.
        val records = listOf(day(0, 80.0), day(7, 80.1), day(2, 79.0))
        assertNull(InsightsEngine.findMostStableDayOfWeek(records))
    }

    // ---- compareStreakWeeks ----

    @Test
    fun compareStreakWeeks_comparesFullStreakVsPartialWeeks() {
        val records = listOf(
            // Week 1: full 7-day streak, -1.4kg
            day(0, 80.0), day(1, 79.8), day(2, 79.6), day(3, 79.4),
            day(4, 79.2), day(5, 79.0), day(6, 78.6),
            // Week 2: partial (2 days logged), -0.2kg
            day(7, 78.6), day(9, 78.4)
        )
        val weeks = InsightsEngine.computeWeeklyChanges(records)
        val correlation = InsightsEngine.compareStreakWeeks(weeks)
        assertEquals(-1.4, correlation!!.fullStreakAvgDeltaKg, 0.001)
        assertEquals(-0.2, correlation.otherWeeksAvgDeltaKg, 0.001)
    }

    @Test
    fun compareStreakWeeks_noFullStreakWeeks_returnsNull() {
        val records = listOf(day(0, 80.0), day(1, 79.0))
        val weeks = InsightsEngine.computeWeeklyChanges(records)
        assertNull(InsightsEngine.compareStreakWeeks(weeks))
    }
}
