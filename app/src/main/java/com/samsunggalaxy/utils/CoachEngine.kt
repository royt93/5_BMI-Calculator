package com.samsunggalaxy.utils

import kotlin.math.abs

/**
 * Idea I9 — local rule-based Coach: extends I2's InsightsEngine from descriptive stats
 * ("what happened") to prescriptive nudges ("what to do next week") — a weekly calorie target
 * and a check-in-frequency suggestion, computed purely from data already in Room (TDEE, goal
 * weight, logging cadence). No LLM/API call, no new backend — same "chi phí bằng 0" positioning
 * as I2. All functions here are pure so they're unit-testable without Room/Context.
 */
object CoachEngine {
    private const val KCAL_PER_KG = 7700.0

    /** A commonly-recommended safe weekly rate of weight change (0.5 kg/week either direction). */
    const val SAFE_WEEKLY_RATE_KG = 0.5

    data class CalorieSuggestion(val targetCalories: Int, val weeklyRateKg: Double)

    /**
     * Weekly calorie target nudging toward goalWeightKg at [SAFE_WEEKLY_RATE_KG], or maintenance
     * calories if already within CalculatorUtils.GOAL_ACHIEVED_TOLERANCE_KG of goal / no goal
     * set. Rounded to the nearest 50 kcal — a false-precision single-digit number would overstate
     * how exact this estimate is. Null if latestTdee isn't a usable positive number.
     */
    fun suggestWeeklyCalorieTarget(latestTdee: Double, currentWeightKg: Double, goalWeightKg: Double?): CalorieSuggestion? {
        if (latestTdee <= 0) return null

        val remaining = goalWeightKg?.let { it - currentWeightKg }
        val weeklyRateKg = when {
            remaining == null || abs(remaining) <= CalculatorUtils.GOAL_ACHIEVED_TOLERANCE_KG -> 0.0
            remaining < 0 -> -SAFE_WEEKLY_RATE_KG
            else -> SAFE_WEEKLY_RATE_KG
        }

        val dailyCalorieAdjustment = weeklyRateKg * KCAL_PER_KG / 7.0
        val target = Math.round((latestTdee + dailyCalorieAdjustment) / 50.0) * 50
        return CalorieSuggestion(targetCalories = target.toInt(), weeklyRateKg = weeklyRateKg)
    }

    enum class CheckInFrequencyAdvice { LOG_MORE_OFTEN, ON_TRACK }

    /**
     * Based on average distinct-days-logged per week over the recent weekly buckets: fewer than
     * 3 days/week on average is too sparse for a reliable trend, so nudges the user to log more
     * often; otherwise the current cadence is already good. Null if there's no weekly data yet.
     */
    fun suggestCheckInFrequency(weeklyChanges: List<InsightsEngine.WeeklyChange>): CheckInFrequencyAdvice? {
        if (weeklyChanges.isEmpty()) return null
        val avgDaysLogged = weeklyChanges.map { it.daysLogged }.average()
        return if (avgDaysLogged < 3.0) CheckInFrequencyAdvice.LOG_MORE_OFTEN else CheckInFrequencyAdvice.ON_TRACK
    }
}
