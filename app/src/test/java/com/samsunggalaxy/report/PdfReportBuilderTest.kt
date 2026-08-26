package com.samsunggalaxy.report

import com.samsunggalaxy.data.BmiRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Idea I8 — PDF health report: pure summary/rows builder. */
class PdfReportBuilderTest {

    private fun record(timestamp: Long, weight: Double, bmi: Double = 22.0) = BmiRecord(
        timestamp = timestamp,
        height = 175.0,
        weight = weight,
        gender = 0,
        age = 30,
        bmi = bmi,
        bmr = 1500.0,
        tdee = 1800.0,
        idealWeightMin = 60.0,
        idealWeightMax = 75.0,
        bodyFatPercentage = null
    )

    @Test
    fun `empty records yields no summary`() {
        assertNull(PdfReportBuilder.buildSummary(emptyList()))
    }

    @Test
    fun `summary uses first and last record chronologically regardless of input order`() {
        val records = listOf(
            record(timestamp = 3000L, weight = 68.0, bmi = 21.0),
            record(timestamp = 1000L, weight = 70.0, bmi = 22.5),
            record(timestamp = 2000L, weight = 69.0, bmi = 21.8)
        )

        val summary = PdfReportBuilder.buildSummary(records)!!

        assertEquals(3, summary.recordCount)
        assertEquals(1000L, summary.startTimestamp)
        assertEquals(3000L, summary.endTimestamp)
        assertEquals(68.0, summary.currentWeightKg, 0.001)
        assertEquals(21.0, summary.currentBmi, 0.001)
        assertEquals(-2.0, summary.weightChangeKg!!, 0.001)
    }

    @Test
    fun `single record has no weight change`() {
        val summary = PdfReportBuilder.buildSummary(listOf(record(timestamp = 1000L, weight = 70.0)))!!
        assertNull(summary.weightChangeKg)
    }

    @Test
    fun `rows are sorted chronologically regardless of input order`() {
        val records = listOf(
            record(timestamp = 3000L, weight = 68.0),
            record(timestamp = 1000L, weight = 70.0),
            record(timestamp = 2000L, weight = 69.0)
        )

        val rows = PdfReportBuilder.buildRows(records)

        assertEquals(listOf(1000L, 2000L, 3000L), rows.map { it.timestamp })
    }
}
