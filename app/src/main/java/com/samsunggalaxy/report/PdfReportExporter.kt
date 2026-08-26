package com.samsunggalaxy.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.samsunggalaxy.R
import com.samsunggalaxy.data.BmiRecord
import com.samsunggalaxy.utils.CalculatorUtils
import com.samsunggalaxy.utils.FILE_PROVIDER_AUTHORITY
import com.samsunggalaxy.utils.UnitFormatter
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Idea I8 — PDF health report export, meant to be handed to a doctor/coach. Same FileProvider
 * pattern as CsvExporter/ShareProgressCardExporter (app-external-files, no WRITE_EXTERNAL_STORAGE
 * needed). PdfDocument/Canvas calls throw in a plain JVM unit test (same reasoning as
 * ShareProgressCardRenderer) — verified via on-device smoke test. The summary numbers themselves
 * come from PdfReportBuilder (pure, unit tested).
 */
object PdfReportExporter {
    // A4 at 72dpi, matching PdfDocument's point-based coordinate system.
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val ROWS_PER_PAGE = 25
    private const val COL_WEIGHT_X = 90f
    private const val COL_HEIGHT_X = 190f
    private const val COL_BMI_X = 290f
    private const val COL_CATEGORY_X = 360f
    private const val CATEGORY_COL_WIDTH = PAGE_WIDTH - MARGIN - MARGIN - COL_CATEGORY_X

    fun export(context: Context, profileName: String, records: List<BmiRecord>, unitSystem: String): Uri? {
        val summary = PdfReportBuilder.buildSummary(records) ?: return null
        val rows = PdfReportBuilder.buildRows(records)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val titlePaint = textPaint(20f, bold = true)
        val sectionPaint = textPaint(13f, bold = true)
        val labelPaint = textPaint(11f)
        val valuePaint = textPaint(11f, bold = true)
        val headerPaint = textPaint(10f, bold = true)
        val rowPaint = textPaint(10f)
        val footerPaint = textPaint(8f, color = Color.GRAY)
        val rulePaint = Paint().apply { color = Color.LTGRAY }

        val document = PdfDocument()
        var pageNumber = 0
        var rowIndex = 0
        do {
            pageNumber++
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            var y = MARGIN

            if (pageNumber == 1) {
                canvas.drawText(context.getString(R.string.pdf_report_title), MARGIN, y, titlePaint)
                y += 28f
                drawEllipsizedText(
                    canvas, context.getString(R.string.pdf_report_profile_label, profileName),
                    MARGIN, y, PAGE_WIDTH - MARGIN - MARGIN, labelPaint
                )
                y += 18f
                canvas.drawText(
                    context.getString(
                        R.string.pdf_report_period_label,
                        dateFormat.format(Date(summary.startTimestamp)),
                        dateFormat.format(Date(summary.endTimestamp))
                    ),
                    MARGIN, y, labelPaint
                )
                y += 26f

                canvas.drawText(context.getString(R.string.pdf_report_summary_heading), MARGIN, y, sectionPaint)
                y += 20f
                y = drawKeyValue(
                    canvas, y, context.getString(R.string.pdf_report_current_weight_label),
                    "%.1f %s".format(
                        Locale.US,
                        UnitFormatter.weightToDisplay(summary.currentWeightKg, unitSystem),
                        UnitFormatter.weightUnitLabel(unitSystem)
                    ),
                    labelPaint, valuePaint
                )
                y = drawKeyValue(
                    canvas, y, context.getString(R.string.pdf_report_current_bmi_label),
                    "%.1f (%s)".format(
                        Locale.US, summary.currentBmi,
                        context.getString(CalculatorUtils.getBMICategoryInfo(summary.currentBmi).labelRes)
                    ),
                    labelPaint, valuePaint
                )
                val deltaText = summary.weightChangeKg?.let { UnitFormatter.formatSignedWeightDelta(it, unitSystem) }
                    ?: context.getString(R.string.pdf_report_no_change)
                y = drawKeyValue(canvas, y, context.getString(R.string.pdf_report_weight_change_label), deltaText, labelPaint, valuePaint)
                y = drawKeyValue(
                    canvas, y, context.getString(R.string.pdf_report_record_count_label),
                    summary.recordCount.toString(), labelPaint, valuePaint
                )
                y += 16f

                canvas.drawText(context.getString(R.string.pdf_report_history_heading), MARGIN, y, sectionPaint)
                y += 20f
            }

            y = drawTableHeader(canvas, y, unitSystem, context, headerPaint, rulePaint)

            var linesOnPage = 0
            while (rowIndex < rows.size && linesOnPage < ROWS_PER_PAGE) {
                y = drawTableRow(canvas, y, rows[rowIndex], unitSystem, dateFormat, context, rowPaint)
                rowIndex++
                linesOnPage++
            }

            canvas.drawText(
                context.getString(R.string.pdf_report_footer, context.getString(R.string.app_name), pageNumber),
                MARGIN, PAGE_HEIGHT - MARGIN / 2, footerPaint
            )
            document.finishPage(page)
        } while (rowIndex < rows.size)

        val dir = File(context.getExternalFilesDir(null), "reports").apply { mkdirs() }
        val file = File(dir, "bmi_report_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { out -> document.writeTo(out) }
        document.close()

        return FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
    }

    private fun textPaint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    private fun drawKeyValue(canvas: Canvas, y: Float, label: String, value: String, labelPaint: Paint, valuePaint: Paint): Float {
        canvas.drawText(label, MARGIN, y, labelPaint)
        canvas.drawText(value, MARGIN + 220f, y, valuePaint)
        return y + 16f
    }

    private fun drawTableHeader(canvas: Canvas, y: Float, unitSystem: String, context: Context, paint: Paint, rulePaint: Paint): Float {
        val weightUnit = UnitFormatter.weightUnitLabel(unitSystem)
        val heightUnit = UnitFormatter.heightUnitLabel(unitSystem)
        canvas.drawText(context.getString(R.string.pdf_report_col_date), MARGIN, y, paint)
        canvas.drawText(context.getString(R.string.pdf_report_col_weight, weightUnit), MARGIN + COL_WEIGHT_X, y, paint)
        canvas.drawText(context.getString(R.string.pdf_report_col_height, heightUnit), MARGIN + COL_HEIGHT_X, y, paint)
        canvas.drawText(context.getString(R.string.pdf_report_col_bmi), MARGIN + COL_BMI_X, y, paint)
        canvas.drawText(context.getString(R.string.pdf_report_col_category), MARGIN + COL_CATEGORY_X, y, paint)
        canvas.drawLine(MARGIN, y + 4f, PAGE_WIDTH - MARGIN, y + 4f, rulePaint)
        return y + 18f
    }

    private fun drawTableRow(
        canvas: Canvas, y: Float, record: BmiRecord, unitSystem: String,
        dateFormat: SimpleDateFormat, context: Context, paint: Paint
    ): Float {
        val weight = UnitFormatter.weightToDisplay(record.weight, unitSystem)
        val height = UnitFormatter.heightToDisplay(record.height, unitSystem)
        val category = context.getString(CalculatorUtils.getBMICategoryInfo(record.bmi).labelRes)
        canvas.drawText(dateFormat.format(Date(record.timestamp)), MARGIN, y, paint)
        canvas.drawText("%.1f".format(Locale.US, weight), MARGIN + COL_WEIGHT_X, y, paint)
        canvas.drawText("%.1f".format(Locale.US, height), MARGIN + COL_HEIGHT_X, y, paint)
        canvas.drawText("%.1f".format(Locale.US, record.bmi), MARGIN + COL_BMI_X, y, paint)
        drawEllipsizedText(canvas, category, MARGIN + COL_CATEGORY_X, y, CATEGORY_COL_WIDTH, paint)
        return y + 16f
    }

    /** Truncates with "…" instead of drawing past maxWidth — long localized labels must not run off the page. */
    private fun drawEllipsizedText(canvas: Canvas, text: String, x: Float, y: Float, maxWidth: Float, paint: Paint) {
        if (paint.measureText(text) <= maxWidth) {
            canvas.drawText(text, x, y, paint)
            return
        }
        val ellipsis = "…"
        val availableWidth = (maxWidth - paint.measureText(ellipsis)).coerceAtLeast(0f)
        val fitCount = paint.breakText(text, true, availableWidth, null)
        canvas.drawText(text.substring(0, fitCount) + ellipsis, x, y, paint)
    }
}
