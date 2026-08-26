package com.samsunggalaxy.utils

import com.samsunggalaxy.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for EPIC-00 (doc/task/todo/EPIC-00-critical-bugs.md):
 * T00.1 goal-weight gain-direction bug, T00.2 activity-level-aware TDEE,
 * T00.3 unified BMI category thresholds, T00.5 gender-neutral "Other" formulas.
 */
class CalculatorUtilsTest {

    // ---- calculateBMI ----

    @Test
    fun calculateBMI_standardCase() {
        // 70kg / (1.75m)^2 = 22.857...
        assertEquals(22.857, CalculatorUtils.calculateBMI(70.0, 175.0), 0.01)
    }

    @Test
    fun calculateBMI_zeroOrNegativeInputs_returnZero() {
        assertEquals(0.0, CalculatorUtils.calculateBMI(0.0, 175.0), 0.0)
        assertEquals(0.0, CalculatorUtils.calculateBMI(70.0, 0.0), 0.0)
        assertEquals(0.0, CalculatorUtils.calculateBMI(-5.0, 175.0), 0.0)
    }

    // ---- getBMICategoryInfo (T00.3: single source of truth, boundaries must match 18.5/25.0/30.0 everywhere) ----

    @Test
    fun getBMICategoryInfo_boundaries() {
        assertEquals(R.string.bmi_category_underweight, CalculatorUtils.getBMICategoryInfo(18.49).labelRes)
        assertEquals(R.string.bmi_category_healthy, CalculatorUtils.getBMICategoryInfo(18.5).labelRes)
        assertEquals(R.string.bmi_category_healthy, CalculatorUtils.getBMICategoryInfo(24.9).labelRes) // was mis-categorized "Overweight" pre-fix
        assertEquals(R.string.bmi_category_overweight, CalculatorUtils.getBMICategoryInfo(25.0).labelRes)
        assertEquals(R.string.bmi_category_overweight, CalculatorUtils.getBMICategoryInfo(29.9).labelRes)
        assertEquals(R.string.bmi_category_obese, CalculatorUtils.getBMICategoryInfo(30.0).labelRes)
    }

    // ---- calculateBMR (T00.5: "Other" must NOT silently equal Female) ----

    @Test
    fun calculateBMR_genderCode_maleAndFemaleMatchLegacyBooleanOverload() {
        val male = CalculatorUtils.calculateBMR(70.0, 175.0, 30, genderCode = 0)
        val female = CalculatorUtils.calculateBMR(70.0, 175.0, 30, genderCode = 1)
        assertEquals(CalculatorUtils.calculateBMR(70.0, 175.0, 30, isMale = true), male, 0.001)
        assertEquals(CalculatorUtils.calculateBMR(70.0, 175.0, 30, isMale = false), female, 0.001)
    }

    @Test
    fun calculateBMR_otherGender_isMidpointNotFemale() {
        val male = CalculatorUtils.calculateBMR(70.0, 175.0, 30, genderCode = 0)
        val female = CalculatorUtils.calculateBMR(70.0, 175.0, 30, genderCode = 1)
        val other = CalculatorUtils.calculateBMR(70.0, 175.0, 30, genderCode = 2)
        assertTrue("Other must differ from Female (regression: 'Other' used to fall into the `else` branch and be treated as Female)", other != female)
        assertEquals((male + female) / 2.0, other, 0.001)
    }

    // ---- calculateIdealWeightRange (T00.5) ----

    @Test
    fun calculateIdealWeightRange_otherGender_isMidpoint() {
        val male = CalculatorUtils.calculateIdealWeightRange(175.0, genderCode = 0)
        val female = CalculatorUtils.calculateIdealWeightRange(175.0, genderCode = 1)
        val other = CalculatorUtils.calculateIdealWeightRange(175.0, genderCode = 2)
        assertTrue(other.first != female.first)
        assertEquals((male.first + female.first) / 2.0, other.first, 0.001)
    }

    // ---- calculateTDEE (T00.2: every activity level must produce a distinct result) ----

    @Test
    fun calculateTDEE_allActivityLevels_areDistinctAndIncreasing() {
        val bmr = 1500.0
        val results = (0..4).map { CalculatorUtils.calculateTDEE(bmr, it) }
        for (i in 0 until results.size - 1) {
            assertTrue("activity level $i should produce less TDEE than ${i + 1}", results[i] < results[i + 1])
        }
        assertEquals(1500.0 * 1.2, CalculatorUtils.calculateTDEE(bmr, 0), 0.001)
        assertEquals(1500.0 * 1.9, CalculatorUtils.calculateTDEE(bmr, 4), 0.001)
    }

    @Test
    fun calculateTDEE_invalidActivityLevel_fallsBackToSedentary() {
        assertEquals(CalculatorUtils.calculateTDEE(1500.0, 0), CalculatorUtils.calculateTDEE(1500.0, 99), 0.001)
    }

    // ---- calculateGoalProgress (T00.1: the core regression — gain goals must NOT report 100% on day one) ----

    @Test
    fun calculateGoalProgress_lossGoal_notYetAchieved() {
        // Started at 80kg, now 75kg, goal 65kg — 5/15kg progressed = 33%
        val p = CalculatorUtils.calculateGoalProgress(startWeight = 80.0, currentWeight = 75.0, goalWeight = 65.0)
        assertTrue(!p.achieved)
        assertTrue(!p.isGainGoal)
        assertEquals(33, p.percent)
        assertEquals(10.0, p.remainingKg, 0.001)
    }

    @Test
    fun calculateGoalProgress_lossGoal_achievedWithinTolerance() {
        val p = CalculatorUtils.calculateGoalProgress(startWeight = 80.0, currentWeight = 65.4, goalWeight = 65.0)
        assertTrue(p.achieved)
        assertEquals(100, p.percent)
    }

    @Test
    fun calculateGoalProgress_gainGoal_notFalselyAchievedOnDayOne() {
        // REGRESSION for ResultAct.kt bug: goalWeight(80) > currentWeight(70) used to make
        // `diff = weight - goalWeight` negative => reported "Achieved" (100%) immediately.
        val p = CalculatorUtils.calculateGoalProgress(startWeight = 70.0, currentWeight = 70.0, goalWeight = 80.0)
        assertTrue("a fresh gain goal must not be immediately 'achieved'", !p.achieved)
        assertTrue(p.isGainGoal)
        assertEquals(0, p.percent)
    }

    @Test
    fun calculateGoalProgress_gainGoal_progressesTowardTarget() {
        val p = CalculatorUtils.calculateGoalProgress(startWeight = 70.0, currentWeight = 75.0, goalWeight = 80.0)
        assertTrue(!p.achieved)
        assertTrue(p.isGainGoal)
        assertEquals(50, p.percent)
        assertEquals(5.0, p.remainingKg, 0.001)
    }

    @Test
    fun calculateGoalProgress_gainGoal_achieved() {
        val p = CalculatorUtils.calculateGoalProgress(startWeight = 70.0, currentWeight = 80.2, goalWeight = 80.0)
        assertTrue(p.achieved)
        assertEquals(100, p.percent)
    }

    @Test
    fun calculateGoalProgress_lossGoal_overshotPastTarget_isAchievedNot99Percent() {
        // Audit regression: startWeight=90, goal=70, user drops to 65 (5kg PAST target).
        // Old logic: abs(65-70)=5 > 1.0 tolerance => achieved=false, percent clamps to 99,
        // remainingKg=5.0 — shows "99% — 5.0kg remaining" to someone who already beat their goal.
        val p = CalculatorUtils.calculateGoalProgress(startWeight = 90.0, currentWeight = 65.0, goalWeight = 70.0)
        assertTrue("overshooting a loss goal must count as achieved", p.achieved)
        assertEquals(100, p.percent)
    }

    @Test
    fun calculateGoalProgress_gainGoal_overshotPastTarget_isAchieved() {
        val p = CalculatorUtils.calculateGoalProgress(startWeight = 60.0, currentWeight = 85.0, goalWeight = 80.0)
        assertTrue("overshooting a gain goal must count as achieved", p.achieved)
        assertEquals(100, p.percent)
    }

    @Test
    fun calculateGoalProgress_startEqualsGoal_noDivideByZero() {
        val p = CalculatorUtils.calculateGoalProgress(startWeight = 70.0, currentWeight = 70.0, goalWeight = 70.0)
        assertTrue(p.achieved) // within 1kg tolerance of itself
        assertEquals(100, p.percent)
    }

    // ---- calculateBodyFat (existing guard from doc/BUGS_FIXED.md #5 — kept as regression coverage) ----

    @Test
    fun calculateBodyFat_invalidMaleMeasurement_neckExceedsWaist_returnsZeroNotCrash() {
        assertEquals(0.0, CalculatorUtils.calculateBodyFat(height = 175.0, waist = 80.0, neck = 90.0, isMale = true), 0.0)
    }

    @Test
    fun calculateBodyFat_validMaleMeasurement_returnsPositiveResult() {
        val result = CalculatorUtils.calculateBodyFat(height = 175.0, waist = 90.0, neck = 38.0, isMale = true)
        assertTrue(result > 0.0)
    }

    // ---- estimateGoalEtaDays (EPIC-07 T07.3) ----

    private val dayMs = 86_400_000L

    @Test
    fun goalEta_fewerThanThreeRecords_notEnoughData() {
        val records = listOf(0L to 80.0, dayMs to 79.0)
        val eta = CalculatorUtils.estimateGoalEtaDays(records, goalWeight = 70.0)
        assertTrue(!eta.hasEnoughData)
        assertTrue(eta.etaDays == null)
    }

    @Test
    fun goalEta_spanUnderTwoDays_notEnoughData() {
        val records = listOf(0L to 80.0, 3_600_000L to 79.8, 7_200_000L to 79.6) // 2 hours total
        val eta = CalculatorUtils.estimateGoalEtaDays(records, goalWeight = 70.0)
        assertTrue(!eta.hasEnoughData)
    }

    @Test
    fun goalEta_lossTrendTowardGoal_computesExpectedDays() {
        // Perfectly linear -1kg/day over 5 days: 80,79,78,77,76
        val records = (0..4).map { i -> (i * dayMs) to (80.0 - i) }
        val eta = CalculatorUtils.estimateGoalEtaDays(records, goalWeight = 70.0)
        assertTrue(eta.hasEnoughData)
        assertEquals(6, eta.etaDays) // (70-76) / -1 = 6 days
    }

    @Test
    fun goalEta_gainTrendTowardGoal_computesExpectedDays() {
        // Perfectly linear +1kg/day over 5 days: 70,71,72,73,74
        val records = (0..4).map { i -> (i * dayMs) to (70.0 + i) }
        val eta = CalculatorUtils.estimateGoalEtaDays(records, goalWeight = 80.0)
        assertTrue(eta.hasEnoughData)
        assertEquals(6, eta.etaDays) // (80-74) / 1 = 6 days
    }

    @Test
    fun goalEta_trendMovingAwayFromGoal_returnsNullEta() {
        // Gaining +1kg/day, but goal is a LOSS goal (60kg) — moving the wrong way.
        val records = (0..4).map { i -> (i * dayMs) to (70.0 + i) }
        val eta = CalculatorUtils.estimateGoalEtaDays(records, goalWeight = 60.0)
        assertTrue(eta.hasEnoughData)
        assertTrue(eta.etaDays == null)
    }

    @Test
    fun goalEta_flatTrend_returnsNullEta() {
        val records = (0..4).map { i -> (i * dayMs) to 75.0 }
        val eta = CalculatorUtils.estimateGoalEtaDays(records, goalWeight = 70.0)
        assertTrue(eta.hasEnoughData)
        assertTrue(eta.etaDays == null)
    }

    // ---- calculateWeightChange (Idea I3 — Share Progress Card) ----

    @Test
    fun weightChange_fewerThanTwoRecords_returnsNull() {
        assertEquals(null, CalculatorUtils.calculateWeightChange(emptyList()))
        assertEquals(null, CalculatorUtils.calculateWeightChange(listOf(0L to 80.0)))
    }

    @Test
    fun weightChange_lossOverPeriod_returnsNegativeDelta() {
        val records = listOf(0L to 80.0, dayMs to 79.0, 2 * dayMs to 77.7)
        val change = CalculatorUtils.calculateWeightChange(records)
        assertEquals(-2.3, change!!, 0.001)
    }

    @Test
    fun weightChange_gainOverPeriod_returnsPositiveDelta() {
        val records = listOf(0L to 70.0, dayMs to 71.2)
        val change = CalculatorUtils.calculateWeightChange(records)
        assertEquals(1.2, change!!, 0.001)
    }

    @Test
    fun weightChange_unsortedInput_stillComputesFirstVsLastByTimestamp() {
        // Deliberately out of chronological order — must sort internally, not trust list order.
        val records = listOf(2 * dayMs to 77.7, 0L to 80.0, dayMs to 79.0)
        val change = CalculatorUtils.calculateWeightChange(records)
        assertEquals(-2.3, change!!, 0.001)
    }

    @Test
    fun weightChange_noChange_returnsZeroDelta() {
        val records = listOf(0L to 75.0, dayMs to 75.0)
        val change = CalculatorUtils.calculateWeightChange(records)
        assertEquals(0.0, change!!, 0.001)
    }

    // ---- unit conversions ----

    @Test
    fun kgLbsRoundTrip() {
        val kg = 70.0
        assertEquals(kg, CalculatorUtils.lbsToKg(CalculatorUtils.kgToLbs(kg)), 0.0001)
    }

    // ---- isSameCalendarDay (EPIC-06 T06.2: gates whether Body Fat can attach onto today's record) ----

    @Test
    fun isSameCalendarDay_sameDayDifferentTime_true() {
        val cal = java.util.Calendar.getInstance()
        cal.set(2026, java.util.Calendar.AUGUST, 23, 8, 0, 0)
        val morning = cal.timeInMillis
        cal.set(2026, java.util.Calendar.AUGUST, 23, 23, 59, 0)
        val night = cal.timeInMillis
        assertTrue(CalculatorUtils.isSameCalendarDay(morning, night))
    }

    @Test
    fun isSameCalendarDay_differentDay_false() {
        val cal = java.util.Calendar.getInstance()
        cal.set(2026, java.util.Calendar.AUGUST, 23, 12, 0, 0)
        val day1 = cal.timeInMillis
        cal.set(2026, java.util.Calendar.AUGUST, 24, 12, 0, 0)
        val day2 = cal.timeInMillis
        assertTrue(!CalculatorUtils.isSameCalendarDay(day1, day2))
    }

    @Test
    fun isSameCalendarDay_sameDayOfYearDifferentYear_false() {
        val cal = java.util.Calendar.getInstance()
        cal.set(2025, java.util.Calendar.AUGUST, 23, 12, 0, 0)
        val lastYear = cal.timeInMillis
        cal.set(2026, java.util.Calendar.AUGUST, 23, 12, 0, 0)
        val thisYear = cal.timeInMillis
        assertTrue(!CalculatorUtils.isSameCalendarDay(lastYear, thisYear))
    }
}
