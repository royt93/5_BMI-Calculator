package com.samsunggalaxy.report

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.samsunggalaxy.data.BmiRecord
import com.samsunggalaxy.utils.UnitFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Idea I8 — PDF health report export. Same before/after-directory-diff pattern as
 * EngagementFeaturesTest's CSV export test — the reports/ directory accumulates PDFs across
 * test runs and manual smoke-testing on a shared emulator, so a diff isolates this call's file.
 * Needs a connected device/emulator: ./gradlew connectedDevDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class PdfReportExporterTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun record(timestamp: Long, weight: Double) = BmiRecord(
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
        bodyFatPercentage = null
    )

    @Test
    fun export_writesValidPdf_andReturnsShareableUri(): Unit = run {
        val records = listOf(
            record(timestamp = 1000L, weight = 70.0),
            record(timestamp = 2000L, weight = 68.5)
        )

        val reportsDir = File(context.getExternalFilesDir(null), "reports")
        val before = reportsDir.listFiles()?.toSet() ?: emptySet()

        val uri = PdfReportExporter.export(context, "Test Profile", records, UnitFormatter.METRIC)
        assertNotNull("export must return a content:// URI when records are non-empty", uri)
        assertEquals("content", uri!!.scheme)

        val after = reportsDir.listFiles()?.toSet() ?: emptySet()
        val newFiles = after - before
        assertEquals("exactly one new PDF file must appear under reports/", 1, newFiles.size)
        val written = newFiles.single()
        assertTrue("PDF file must exist on disk under app-external-files/reports", written.exists())
        assertEquals("%PDF", written.readBytes().copyOfRange(0, 4).toString(Charsets.US_ASCII))
        val deleted = written.delete()
        assertTrue(deleted)
    }

    @Test
    fun export_emptyList_returnsNull() {
        assertEquals(null, PdfReportExporter.export(context, "Test Profile", emptyList(), UnitFormatter.METRIC))
    }
}
