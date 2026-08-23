package com.samsunggalaxy.ui

import com.samsunggalaxy.utils.UnitFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** EPIC-04 T04.1 — MainAct wheel-picker range generation, pure and unit-testable. */
class MainActWheelLabelsTest {

    @Test
    fun weightWheelLabels_metric_is1to151() {
        val labels = weightWheelLabels(UnitFormatter.METRIC)
        assertEquals(151, labels.size)
        assertEquals("1", labels.first())
        assertEquals("151", labels.last())
    }

    @Test
    fun weightWheelLabels_imperial_coversFullMetricRange() {
        // Regression (audit finding): a hardcoded "2..330" undershot 151kg's true lbs
        // equivalent (~332.9, rounds to 333) — the wheel had no label to snap to at max
        // weight and silently fell back to the minimum. The imperial max must be able to
        // display whatever `UnitFormatter.weightToDisplay(151kg)` rounds to.
        val labels = weightWheelLabels(UnitFormatter.IMPERIAL)
        assertEquals("2", labels.first())
        val maxKgAsDisplay = Math.round(UnitFormatter.weightToDisplay(151.0, UnitFormatter.IMPERIAL)).toString()
        assertTrue(
            "imperial labels must include '$maxKgAsDisplay' (151kg's rounded lbs equivalent), got up to '${labels.last()}'",
            labels.contains(maxKgAsDisplay)
        )
    }

    @Test
    fun heightWheelLabels_metric_is1to229() {
        val labels = heightWheelLabels(UnitFormatter.METRIC)
        assertEquals(229, labels.size)
        assertEquals("1", labels.first())
        assertEquals("229", labels.last())
    }

    @Test
    fun heightWheelLabels_imperial_coversFullMetricRange() {
        val labels = heightWheelLabels(UnitFormatter.IMPERIAL)
        assertEquals("1", labels.first())
        val maxCmAsDisplay = Math.round(UnitFormatter.heightToDisplay(229.0, UnitFormatter.IMPERIAL)).toString()
        assertTrue(
            "imperial labels must include '$maxCmAsDisplay' (229cm's rounded inch equivalent), got up to '${labels.last()}'",
            labels.contains(maxCmAsDisplay)
        )
    }
}
