package com.samsunggalaxy.utils

import com.samsunggalaxy.R
import com.samsunggalaxy.data.BmiRecord
import kotlin.math.pow

object CalculatorUtils {

    /**
     * Calculate BMI
     * BMI = weight(kg) / (height(cm) / 100)^2
     */
    fun calculateBMI(weight: Double, height: Double): Double {
        if (height <= 0 || weight <= 0) return 0.0
        return (weight / ((height / 100).pow(2)))
    }

    /** Single source of truth for BMI category boundaries (label/color/tips resources). */
    data class BmiCategoryInfo(val labelRes: Int, val colorRes: Int, val tipsArrayRes: Int)

    fun getBMICategoryInfo(bmi: Double): BmiCategoryInfo = when {
        bmi < 18.5 -> BmiCategoryInfo(R.string.bmi_category_underweight, R.color.bmi_underweight, R.array.tips_underweight)
        bmi < 25.0 -> BmiCategoryInfo(R.string.bmi_category_healthy, R.color.bmi_healthy, R.array.tips_healthy)
        bmi < 30.0 -> BmiCategoryInfo(R.string.bmi_category_overweight, R.color.bmi_overweight, R.array.tips_overweight)
        else -> BmiCategoryInfo(R.string.bmi_category_obese, R.color.bmi_obese, R.array.tips_obese)
    }

    /**
     * Calculate BMR (Basal Metabolic Rate) using Mifflin-St Jeor Equation
     * Men: BMR = 10W + 6.25H - 5A + 5
     * Women: BMR = 10W + 6.25H - 5A - 161
     */
    fun calculateBMR(weight: Double, height: Double, age: Int, isMale: Boolean): Double {
        val base = 10 * weight + 6.25 * height - 5 * age
        return if (isMale) base + 5 else base - 161
    }

    /**
     * BMR for 3-way gender input (0=Male, 1=Female, 2=Other).
     * "Other" uses the midpoint of the male/female offsets instead of silently
     * defaulting to the female formula.
     */
    fun calculateBMR(weight: Double, height: Double, age: Int, genderCode: Int): Double {
        val base = 10 * weight + 6.25 * height - 5 * age
        return when (genderCode) {
            0 -> base + 5
            1 -> base - 161
            else -> base - 78.0 // midpoint of +5 and -161
        }
    }

    /**
     * Calculate TDEE (Total Daily Energy Expenditure)
     * TDEE = BMR × Activity Factor
     * Activity levels:
     * 0 = Sedentary (1.2)
     * 1 = Lightly Active (1.375)
     * 2 = Moderately Active (1.55)
     * 3 = Very Active (1.725)
     * 4 = Super Active (1.9)
     */
    fun calculateTDEE(bmr: Double, activityLevel: Int): Double {
        val activityFactor = when (activityLevel) {
            0 -> 1.2
            1 -> 1.375
            2 -> 1.55
            3 -> 1.725
            4 -> 1.9
            else -> 1.2
        }
        return bmr * activityFactor
    }

    /**
     * Calculate ideal weight range using Devine formula
     * Men: 50 + 2.3 × (height(inches) - 60)
     * Women: 45.5 + 2.3 × (height(inches) - 60)
     */
    fun calculateIdealWeightRange(height: Double, isMale: Boolean): Pair<Double, Double> {
        if (height <= 0) return Pair(0.0, 0.0)

        val heightInInches = height / 2.54
        val base = if (isMale) 50.0 else 45.5
        val ideal = if (heightInInches > 60) {
            base + 2.3 * (heightInInches - 60)
        } else {
            base
        }
        // Return range ±5kg, but ensure minimum is not negative
        val min = maxOf(ideal - 5, 30.0) // Minimum reasonable weight is 30kg
        val max = ideal + 5
        return Pair(min, max)
    }

    /** Ideal weight range for 3-way gender input (0=Male, 1=Female, 2=Other — midpoint base). */
    fun calculateIdealWeightRange(height: Double, genderCode: Int): Pair<Double, Double> {
        if (height <= 0) return Pair(0.0, 0.0)

        val heightInInches = height / 2.54
        val base = when (genderCode) {
            0 -> 50.0
            1 -> 45.5
            else -> 47.75 // midpoint of 50.0 and 45.5
        }
        val ideal = if (heightInInches > 60) base + 2.3 * (heightInInches - 60) else base
        val min = maxOf(ideal - 5, 30.0)
        val max = ideal + 5
        return Pair(min, max)
    }

    /**
     * Idea I6 — Quick-log Notification Action: builds a weight-only follow-up record reusing
     * [last]'s height/age/gender, the same shape HistoryActivity's quick-log FAB (T07.5) builds
     * inline. Shared here so the FAB and the new notification quick-log action can't drift.
     */
    fun buildQuickLogRecord(
        last: BmiRecord,
        newWeightKg: Double,
        activityLevel: Int,
        timestampMs: Long = System.currentTimeMillis()
    ): BmiRecord {
        val bmi = calculateBMI(newWeightKg, last.height)
        val bmr = calculateBMR(newWeightKg, last.height, last.age, last.gender)
        val tdee = calculateTDEE(bmr, activityLevel)
        val idealWeight = calculateIdealWeightRange(last.height, last.gender)
        return BmiRecord(
            timestamp = timestampMs,
            height = last.height,
            weight = newWeightKg,
            gender = last.gender,
            age = last.age,
            bmi = bmi,
            bmr = bmr,
            tdee = tdee,
            idealWeightMin = idealWeight.first,
            idealWeightMax = idealWeight.second,
            bodyFatPercentage = null,
            profileId = last.profileId
        )
    }

    /** Tolerance (kg) within which current weight counts as having reached the goal. */
    const val GOAL_ACHIEVED_TOLERANCE_KG = 1.0

    /** Result of comparing progress toward a goal weight, direction-aware (loss OR gain). */
    data class GoalProgress(
        val percent: Int,
        val achieved: Boolean,
        val remainingKg: Double,
        val isGainGoal: Boolean
    )

    /**
     * Direction-aware goal progress. `startWeight` is the baseline (e.g. earliest tracked
     * weight) used to compute % progress; `currentWeight`/`goalWeight` determine direction
     * and remaining distance. Handles both weight-loss goals (goalWeight < startWeight) and
     * weight-gain goals (goalWeight > startWeight) — a fixed "diff <= 0 => achieved" check
     * incorrectly reports gain goals as 100% achieved on day one. Also handles overshoot
     * (past the goal, beyond tolerance) as achieved rather than "99% — Xkg remaining".
     */
    fun calculateGoalProgress(startWeight: Double, currentWeight: Double, goalWeight: Double): GoalProgress {
        val isGainGoal = goalWeight >= startWeight
        val withinTolerance = kotlin.math.abs(currentWeight - goalWeight) <= GOAL_ACHIEVED_TOLERANCE_KG
        val overshot = if (isGainGoal) currentWeight > goalWeight else currentWeight < goalWeight
        val achieved = withinTolerance || overshot
        val totalDistance = kotlin.math.abs(goalWeight - startWeight)
        val progressed = if (isGainGoal) currentWeight - startWeight else startWeight - currentWeight
        val percent = when {
            achieved -> 100
            totalDistance <= 0.0 -> 0
            else -> ((progressed / totalDistance) * 100).toInt().coerceIn(0, 99)
        }
        val remainingKg = kotlin.math.abs(currentWeight - goalWeight)
        return GoalProgress(percent, achieved, remainingKg, isGainGoal)
    }

    /**
     * Calculate body fat percentage using Navy formula
     * Men: 495 / (1.0324 - 0.19077 × log10(waist - neck) + 0.15456 × log10(height)) - 450
     * Women: 495 / (1.29579 - 0.35004 × log10(waist + hip - neck) + 0.22100 × log10(height)) - 450
     */
    fun calculateBodyFat(
        height: Double,
        waist: Double,
        neck: Double,
        hip: Double? = null,
        isMale: Boolean
    ): Double {
        // Validate inputs
        if (height <= 0 || waist <= 0 || neck <= 0) return 0.0

        return if (isMale) {
            val waistMinusNeck = waist - neck
            if (waistMinusNeck <= 0) return 0.0 // Invalid: neck must be smaller than waist

            val result = 495 / (1.0324 - 0.19077 * kotlin.math.log10(waistMinusNeck) + 0.15456 * kotlin.math.log10(height)) - 450
            // Body fat cannot be negative
            if (result < 0) 0.0 else result
        } else {
            if (hip == null || hip <= 0) return 0.0

            val waistPlusHipMinusNeck = waist + hip - neck
            if (waistPlusHipMinusNeck <= 0) return 0.0 // Invalid measurement

            val result = 495 / (1.29579 - 0.35004 * kotlin.math.log10(waistPlusHipMinusNeck) + 0.22100 * kotlin.math.log10(height)) - 450
            // Body fat cannot be negative
            if (result < 0) 0.0 else result
        }
    }

    /**
     * Calculate recommended water intake
     * Basic: weight(kg) × 0.033 liters
     */
    fun calculateWaterIntake(weight: Double): Double {
        return weight * 0.033
    }

    /** ETA estimate toward a goal weight, from a simple linear-regression trend. */
    data class GoalEta(val etaDays: Int?, val hasEnoughData: Boolean)

    /**
     * Estimate days-to-goal via least-squares linear regression over `records`
     * (timestamp millis, weight kg — any order; sorted internally by timestamp).
     * Requires >=3 points spread over >=2 days, and a trend actually moving toward
     * the goal — otherwise returns etaDays=null so the UI can show a neutral state
     * instead of a misleading number from too little/contradictory data.
     */
    fun estimateGoalEtaDays(records: List<Pair<Long, Double>>, goalWeight: Double): GoalEta {
        val sorted = records.sortedBy { it.first }
        if (sorted.size < 3) return GoalEta(null, false)

        val spanMillis = sorted.last().first - sorted.first().first
        val minSpanMillis = 2L * 24 * 60 * 60 * 1000
        if (spanMillis < minSpanMillis) return GoalEta(null, false)

        val firstTimestamp = sorted.first().first
        val daysSinceFirst = sorted.map { (it.first - firstTimestamp) / 86_400_000.0 }
        val weights = sorted.map { it.second }
        val n = sorted.size
        val sumX = daysSinceFirst.sum()
        val sumY = weights.sum()
        val sumXY = daysSinceFirst.zip(weights).sumOf { it.first * it.second }
        val sumXX = daysSinceFirst.sumOf { it * it }
        val denom = n * sumXX - sumX * sumX
        if (denom == 0.0) return GoalEta(null, true)
        val slopePerDay = (n * sumXY - sumX * sumY) / denom

        if (kotlin.math.abs(slopePerDay) < 0.001) return GoalEta(null, true) // flat trend

        val diff = goalWeight - weights.last()
        val daysToGoal = diff / slopePerDay
        if (daysToGoal <= 0) return GoalEta(null, true) // trend moving away from goal (or already there)

        return GoalEta(daysToGoal.toInt().coerceAtMost(3650), true)
    }

    /**
     * Idea I3 — Share Progress Card: simple first-vs-last weight delta (kg) over a window.
     * `records` (timestamp millis, weight kg) should already be filtered to the desired window
     * by the caller (e.g. `BmiRepository.getRecordsSince`) — this just does the arithmetic,
     * sorted internally by timestamp so caller order doesn't matter. Returns null with fewer
     * than 2 records (no meaningful "change" to report).
     */
    fun calculateWeightChange(records: List<Pair<Long, Double>>): Double? {
        if (records.size < 2) return null
        val sorted = records.sortedBy { it.first }
        return sorted.last().second - sorted.first().second
    }

    /**
     * Convert kg to lbs
     */
    fun kgToLbs(kg: Double): Double = kg * 2.20462

    /**
     * Convert lbs to kg
     */
    fun lbsToKg(lbs: Double): Double = lbs / 2.20462

    /**
     * Convert cm to inches
     */
    fun cmToInches(cm: Double): Double = cm / 2.54

    /**
     * Convert inches to cm
     */
    fun inchesToCm(inches: Double): Double = inches * 2.54

    /** EPIC-06 T06.2 — used to decide whether Body Fat can attach onto today's weigh-in record. */
    fun isSameCalendarDay(timestamp1: Long, timestamp2: Long): Boolean {
        val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = timestamp1 }
        val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = timestamp2 }
        return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
            cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
    }
}
