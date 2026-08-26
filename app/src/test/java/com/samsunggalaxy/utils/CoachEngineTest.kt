package com.samsunggalaxy.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoachEngineTest {

    // ---- suggestWeeklyCalorieTarget ----

    @Test
    fun calorieTarget_noGoal_suggestsMaintenanceAtTdee() {
        val suggestion = CoachEngine.suggestWeeklyCalorieTarget(latestTdee = 2000.0, currentWeightKg = 80.0, goalWeightKg = null)!!
        assertEquals(2000, suggestion.targetCalories)
        assertEquals(0.0, suggestion.weeklyRateKg, 0.001)
    }

    @Test
    fun calorieTarget_goalBelowCurrent_suggestsDeficit() {
        val suggestion = CoachEngine.suggestWeeklyCalorieTarget(latestTdee = 2000.0, currentWeightKg = 80.0, goalWeightKg = 70.0)!!
        assertEquals(-CoachEngine.SAFE_WEEKLY_RATE_KG, suggestion.weeklyRateKg, 0.001)
        assertEquals(1450, suggestion.targetCalories) // 2000 - (0.5*7700/7) = 2000 - 550 = 1450
    }

    @Test
    fun calorieTarget_goalAboveCurrent_suggestsSurplus() {
        val suggestion = CoachEngine.suggestWeeklyCalorieTarget(latestTdee = 2000.0, currentWeightKg = 60.0, goalWeightKg = 70.0)!!
        assertEquals(CoachEngine.SAFE_WEEKLY_RATE_KG, suggestion.weeklyRateKg, 0.001)
        assertEquals(2550, suggestion.targetCalories) // 2000 + 550
    }

    @Test
    fun calorieTarget_withinToleranceOfGoal_suggestsMaintenance() {
        // 0.5kg away from goal — inside CalculatorUtils.GOAL_ACHIEVED_TOLERANCE_KG (1.0kg).
        val suggestion = CoachEngine.suggestWeeklyCalorieTarget(latestTdee = 2000.0, currentWeightKg = 70.5, goalWeightKg = 70.0)!!
        assertEquals(0.0, suggestion.weeklyRateKg, 0.001)
        assertEquals(2000, suggestion.targetCalories)
    }

    @Test
    fun calorieTarget_nonPositiveTdee_returnsNull() {
        assertNull(CoachEngine.suggestWeeklyCalorieTarget(latestTdee = 0.0, currentWeightKg = 80.0, goalWeightKg = 70.0))
        assertNull(CoachEngine.suggestWeeklyCalorieTarget(latestTdee = -100.0, currentWeightKg = 80.0, goalWeightKg = 70.0))
    }

    @Test
    fun calorieTarget_roundsToNearest50Kcal() {
        // TDEE 2013 with no goal -> maintenance target rounds 2013 to 2000.
        val suggestion = CoachEngine.suggestWeeklyCalorieTarget(latestTdee = 2013.0, currentWeightKg = 80.0, goalWeightKg = null)!!
        assertEquals(2000, suggestion.targetCalories)
    }

    // ---- suggestCheckInFrequency ----

    private fun week(daysLogged: Int) = InsightsEngine.WeeklyChange(weekStartEpochDay = 0L, deltaKg = 0.0, daysLogged = daysLogged)

    @Test
    fun checkInFrequency_emptyWeeklyChanges_returnsNull() {
        assertNull(CoachEngine.suggestCheckInFrequency(emptyList()))
    }

    @Test
    fun checkInFrequency_sparseLogging_suggestsLogMoreOften() {
        val advice = CoachEngine.suggestCheckInFrequency(listOf(week(1), week(2)))
        assertEquals(CoachEngine.CheckInFrequencyAdvice.LOG_MORE_OFTEN, advice)
    }

    @Test
    fun checkInFrequency_consistentLogging_suggestsOnTrack() {
        val advice = CoachEngine.suggestCheckInFrequency(listOf(week(5), week(7)))
        assertEquals(CoachEngine.CheckInFrequencyAdvice.ON_TRACK, advice)
    }

    @Test
    fun checkInFrequency_exactlyThreeDaysAverage_suggestsOnTrack() {
        // Boundary case: average of exactly 3.0 must count as "on track" (< 3.0 is the sparse threshold).
        val advice = CoachEngine.suggestCheckInFrequency(listOf(week(3)))
        assertEquals(CoachEngine.CheckInFrequencyAdvice.ON_TRACK, advice)
    }
}
