package com.samsunggalaxy.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/** EPIC-04 T04.1 — metric<->imperial conversion/formatting. */
class UnitFormatterTest {

    @Test
    fun metric_passesThroughUnchanged() {
        assertEquals(70.0, UnitFormatter.weightToDisplay(70.0, UnitFormatter.METRIC), 0.001)
        assertEquals(175.0, UnitFormatter.heightToDisplay(175.0, UnitFormatter.METRIC), 0.001)
        assertEquals("kg", UnitFormatter.weightUnitLabel(UnitFormatter.METRIC))
        assertEquals("cm", UnitFormatter.heightUnitLabel(UnitFormatter.METRIC))
    }

    @Test
    fun imperial_convertsWeightToLbs() {
        assertEquals(154.32, UnitFormatter.weightToDisplay(70.0, UnitFormatter.IMPERIAL), 0.01)
        assertEquals("lbs", UnitFormatter.weightUnitLabel(UnitFormatter.IMPERIAL))
    }

    @Test
    fun imperial_convertsHeightToInches() {
        assertEquals(68.9, UnitFormatter.heightToDisplay(175.0, UnitFormatter.IMPERIAL), 0.1)
        assertEquals("in", UnitFormatter.heightUnitLabel(UnitFormatter.IMPERIAL))
    }

    @Test
    fun weightToMetric_roundTripsWithWeightToDisplay() {
        val originalKg = 82.5
        val lbs = UnitFormatter.weightToDisplay(originalKg, UnitFormatter.IMPERIAL)
        val backToKg = UnitFormatter.weightToMetric(lbs, UnitFormatter.IMPERIAL)
        assertEquals(originalKg, backToKg, 0.0001)
    }

    @Test
    fun heightToMetric_roundTripsWithHeightToDisplay() {
        val originalCm = 168.0
        val inches = UnitFormatter.heightToDisplay(originalCm, UnitFormatter.IMPERIAL)
        val backToCm = UnitFormatter.heightToMetric(inches, UnitFormatter.IMPERIAL)
        assertEquals(originalCm, backToCm, 0.0001)
    }

    @Test
    fun weightToMetric_metricSystem_isIdentity() {
        assertEquals(70.0, UnitFormatter.weightToMetric(70.0, UnitFormatter.METRIC), 0.001)
    }

    @Test
    fun formatWeight_metric() {
        assertEquals("70.0 kg", UnitFormatter.formatWeight(70.0, UnitFormatter.METRIC))
    }

    @Test
    fun formatWeight_imperial() {
        assertEquals("154.3 lbs", UnitFormatter.formatWeight(70.0, UnitFormatter.IMPERIAL))
    }

    @Test
    fun formatHeight_metric() {
        assertEquals("175.0 cm", UnitFormatter.formatHeight(175.0, UnitFormatter.METRIC))
    }

    @Test
    fun formatHeight_imperial() {
        assertEquals("68.9 in", UnitFormatter.formatHeight(175.0, UnitFormatter.IMPERIAL))
    }
}
