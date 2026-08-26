package com.samsunggalaxy.utils

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.sqrt

/**
 * Idea I2 — Smart Insights: on-device statistics over a profile's existing weigh-in history, no
 * API/LLM calls. All functions here are pure (timestamp millis + weight kg in, records already
 * loaded by the caller e.g. HistoryActivity) so they're unit-testable without Room/Context.
 *
 * java.time is safe here across the app's full minSdk 24 range — core library desugaring was
 * enabled in EPIC-09 (app/build.gradle.kts) specifically so java.time works below API 26.
 */
object InsightsEngine {
    /** One calendar week (Mon-Sun, local timezone). `daysLogged` = distinct calendar days with a record. */
    data class WeeklyChange(val weekStartEpochDay: Long, val deltaKg: Double, val daysLogged: Int)

    private fun epochDay(timestampMs: Long): Long =
        Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

    private fun weekStartEpochDay(timestampMs: Long): Long =
        Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault()).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()

    private fun dayOfWeek(timestampMs: Long): DayOfWeek =
        Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault()).dayOfWeek

    /** Buckets `records` (timestamp millis, weight kg) into calendar weeks. */
    fun computeWeeklyChanges(records: List<Pair<Long, Double>>): List<WeeklyChange> {
        return records.groupBy { weekStartEpochDay(it.first) }
            .map { (weekStart, weekRecords) ->
                val sorted = weekRecords.sortedBy { it.first }
                val delta = if (sorted.size >= 2) sorted.last().second - sorted.first().second else 0.0
                val daysLogged = sorted.map { epochDay(it.first) }.distinct().size
                WeeklyChange(weekStart, delta, daysLogged)
            }
            .sortedBy { it.weekStartEpochDay }
    }

    /** Most weight lost in a single week — null if no week has >=2 records to compute a delta from. */
    fun findBestWeek(weeklyChanges: List<WeeklyChange>): WeeklyChange? =
        weeklyChanges.filter { it.daysLogged >= 2 }.minByOrNull { it.deltaKg }

    /**
     * The weekday whose logged weights vary the least (lowest population stddev), among weekdays
     * with >=2 samples. Null if fewer than 2 such weekdays exist — "most stable" is meaningless
     * without at least two weekdays to compare.
     */
    fun findMostStableDayOfWeek(records: List<Pair<Long, Double>>): DayOfWeek? {
        val byWeekday = records.groupBy { dayOfWeek(it.first) }
            .mapValues { (_, recs) -> recs.map { it.second } }
            .filterValues { it.size >= 2 }
        if (byWeekday.size < 2) return null

        return byWeekday.minByOrNull { (_, weights) -> stdDev(weights) }?.key
    }

    private fun stdDev(values: List<Double>): Double {
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return sqrt(variance)
    }

    /** Average weekly delta for full-streak (logged every day that week) vs. other weeks. */
    data class StreakCorrelation(val fullStreakAvgDeltaKg: Double, val otherWeeksAvgDeltaKg: Double)

    /**
     * Compares average weekly weight change between weeks where the user logged all 7 days
     * ("full streak week") and weeks where they logged fewer days (but still >=2, so a delta is
     * meaningful). Null if either group is empty — nothing to compare.
     */
    fun compareStreakWeeks(weeklyChanges: List<WeeklyChange>): StreakCorrelation? {
        val qualifying = weeklyChanges.filter { it.daysLogged >= 2 }
        val fullStreak = qualifying.filter { it.daysLogged == 7 }
        val other = qualifying.filter { it.daysLogged < 7 }
        if (fullStreak.isEmpty() || other.isEmpty()) return null

        return StreakCorrelation(
            fullStreakAvgDeltaKg = fullStreak.map { it.deltaKg }.average(),
            otherWeeksAvgDeltaKg = other.map { it.deltaKg }.average()
        )
    }
}
