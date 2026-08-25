package com.samsunggalaxy.utils

import com.samsunggalaxy.data.BmiRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit coverage for EPIC-08 T08.2 (doc/task/todo/EPIC-08-engagement-features.md): the pure
 * CSV row-building logic, independent of Context/file I/O (see CsvExporter.exportBmiRecords).
 */
class CsvExporterTest {

    private fun record(timestamp: Long, weight: Double, bodyFat: Double? = null) = BmiRecord(
        timestamp = timestamp,
        height = 175.0,
        weight = weight,
        gender = 0,
        age = 30,
        bmi = weight / (1.75 * 1.75),
        bmr = 1500.0,
        tdee = 2000.0,
        idealWeightMin = 60.0,
        idealWeightMax = 75.0,
        bodyFatPercentage = bodyFat,
        profileId = 1L
    )

    @Test
    fun header_listsAllColumns() {
        val csv = CsvExporter.buildCsvContent(listOf(record(1000L, 70.0)), UnitFormatter.METRIC)
        val header = csv.lineSequence().first()
        assertEquals("Date,Weight (kg),Height (cm),BMI,BMR (cal/day),TDEE (cal/day),Body Fat (%)", header)
    }

    @Test
    fun rowCount_matchesRecordCount() {
        val records = listOf(record(1000L, 70.0), record(2000L, 71.0), record(3000L, 72.0))
        val csv = CsvExporter.buildCsvContent(records, UnitFormatter.METRIC)
        // header + 3 data rows, trailing newline after the last row
        assertEquals(4, csv.lineSequence().filter { it.isNotEmpty() }.count())
    }

    @Test
    fun rows_sortedByTimestamp_regardlessOfInputOrder() {
        val records = listOf(record(3000L, 72.0), record(1000L, 70.0), record(2000L, 71.0))
        val csv = CsvExporter.buildCsvContent(records, UnitFormatter.METRIC)
        val dataLines = csv.lineSequence().drop(1).filter { it.isNotEmpty() }.toList()
        // weight is the 2nd column — earliest timestamp (70.0) must come first
        assertTrue(dataLines[0].startsWith("1970-01-01,70.0"))
        assertTrue(dataLines[2].startsWith("1970-01-01,72.0"))
    }

    @Test
    fun numbers_useDotDecimalSeparator_neverComma() {
        // Regression: a comma decimal separator would corrupt CSV's own ',' column delimiter —
        // same locale bug class as EPIC-06's toDoubleOrNull() prefill fix, applied to export.
        val csv = CsvExporter.buildCsvContent(listOf(record(1000L, 70.5, bodyFat = 18.25)), UnitFormatter.METRIC)
        val dataLine = csv.lineSequence().drop(1).first { it.isNotEmpty() }
        assertEquals(7, dataLine.split(",").size)
        assertTrue(dataLine.contains("70.5"))
    }

    @Test
    fun bodyFatNull_rendersAsEmptyColumn_notZero() {
        val csv = CsvExporter.buildCsvContent(listOf(record(1000L, 70.0, bodyFat = null)), UnitFormatter.METRIC)
        val dataLine = csv.lineSequence().drop(1).first { it.isNotEmpty() }
        assertEquals("", dataLine.split(",").last())
    }

    @Test
    fun imperialUnitSystem_convertsWeightAndHeight() {
        val csv = CsvExporter.buildCsvContent(listOf(record(1000L, 70.0)), UnitFormatter.IMPERIAL)
        val header = csv.lineSequence().first()
        assertTrue(header.contains("Weight (lbs)"))
        assertTrue(header.contains("Height (in)"))
        val dataLine = csv.lineSequence().drop(1).first { it.isNotEmpty() }
        // 70kg ~= 154.3 lbs
        assertFalse(dataLine.contains(",70.0,"))
    }
}
