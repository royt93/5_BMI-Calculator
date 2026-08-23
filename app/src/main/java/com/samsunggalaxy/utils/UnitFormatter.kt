package com.samsunggalaxy.utils

/**
 * Single source of truth for metric<->imperial conversion/formatting (EPIC-04 T04.1).
 * `BmiRecord`/`Profile` always store metric in Room — only the display layer (and MainAct's
 * input wheels, converted back to metric before saving) ever deal with imperial units.
 *
 * Scope note: goal-weight strings that are localized templates with "kg" baked into the
 * translated string itself (`goal_weight_current`/`target`/`remaining`, the `suffixText="kg"`
 * on the goal/quick-log dialogs) are NOT unit-aware yet — that needs imperial-variant string
 * templates across 17 locales, a separate follow-up. Everywhere this formatter is used, the
 * unit suffix is produced in code, not baked into a translated template.
 */
object UnitFormatter {
    const val METRIC = "metric"
    const val IMPERIAL = "imperial"

    fun weightUnitLabel(unitSystem: String): String = if (unitSystem == IMPERIAL) "lbs" else "kg"
    fun heightUnitLabel(unitSystem: String): String = if (unitSystem == IMPERIAL) "in" else "cm"

    fun weightToDisplay(kg: Double, unitSystem: String): Double =
        if (unitSystem == IMPERIAL) CalculatorUtils.kgToLbs(kg) else kg

    fun weightToMetric(value: Double, unitSystem: String): Double =
        if (unitSystem == IMPERIAL) CalculatorUtils.lbsToKg(value) else value

    fun heightToDisplay(cm: Double, unitSystem: String): Double =
        if (unitSystem == IMPERIAL) CalculatorUtils.cmToInches(cm) else cm

    fun heightToMetric(value: Double, unitSystem: String): Double =
        if (unitSystem == IMPERIAL) CalculatorUtils.inchesToCm(value) else value

    fun formatWeight(kg: Double, unitSystem: String): String =
        String.format("%.1f %s", weightToDisplay(kg, unitSystem), weightUnitLabel(unitSystem))

    fun formatHeight(cm: Double, unitSystem: String): String =
        String.format("%.1f %s", heightToDisplay(cm, unitSystem), heightUnitLabel(unitSystem))
}
