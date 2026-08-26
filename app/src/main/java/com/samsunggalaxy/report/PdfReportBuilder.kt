package com.samsunggalaxy.report

import com.samsunggalaxy.data.BmiRecord
import com.samsunggalaxy.utils.CalculatorUtils

/** Idea I8 — PDF health report. Summary numbers for the report's first page. */
data class PdfReportSummary(
    val recordCount: Int,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val currentWeightKg: Double,
    val currentBmi: Double,
    val weightChangeKg: Double?
)

/**
 * Idea I8 — PDF health report export ("for your doctor/coach"). Pure — no Context/PdfDocument
 * dependency, so unit-testable independent of the Canvas-based rendering in PdfReportExporter
 * (same split as CalculatorUtils.calculateWeightChange / ShareProgressCardRenderer).
 */
object PdfReportBuilder {

    fun buildSummary(records: List<BmiRecord>): PdfReportSummary? {
        if (records.isEmpty()) return null
        val sorted = records.sortedBy { it.timestamp }
        val first = sorted.first()
        val last = sorted.last()
        return PdfReportSummary(
            recordCount = sorted.size,
            startTimestamp = first.timestamp,
            endTimestamp = last.timestamp,
            currentWeightKg = last.weight,
            currentBmi = last.bmi,
            weightChangeKg = CalculatorUtils.calculateWeightChange(sorted.map { it.timestamp to it.weight })
        )
    }

    /** Chronological rows for the report's history table. */
    fun buildRows(records: List<BmiRecord>): List<BmiRecord> = records.sortedBy { it.timestamp }
}
