package com.samsunggalaxy.utils

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

    /**
     * Get BMI category
     */
    fun getBMICategory(bmi: Double): String {
        return when {
            bmi < 18.5 -> "Underweight"
            bmi < 24.9 -> "Healthy"
            bmi < 30.0 -> "Overweight"
            else -> "Obesity"
        }
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
}
