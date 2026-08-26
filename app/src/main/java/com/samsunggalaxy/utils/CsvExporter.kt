package com.samsunggalaxy.utils

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.samsunggalaxy.data.BmiRecord
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * EPIC-08 T08.2 — CSV export of BMI history. Written to app-external-files (no
 * WRITE_EXTERNAL_STORAGE needed on any API level) and shared via the FileProvider already
 * declared in the manifest, matching the doc's "MediaStore/FileProvider scoped, not a broad
 * storage permission" recommendation.
 */
object CsvExporter {

    /**
     * Locale.US throughout — CSV uses ',' as the column delimiter, so a comma-decimal locale
     * (de/fr/ru/...) rendering "70,5" would corrupt the column count on top of the same
     * toDoubleOrNull()-vs-default-locale mismatch fixed in EPIC-06.
     */
    /** Pure — exposed for unit testing independent of Context/file I/O. */
    fun buildCsvContent(records: List<BmiRecord>, unitSystem: String): String {
        val weightUnit = UnitFormatter.weightUnitLabel(unitSystem)
        val heightUnit = UnitFormatter.heightUnitLabel(unitSystem)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        val sb = StringBuilder("Date,Weight ($weightUnit),Height ($heightUnit),BMI,BMR (cal/day),TDEE (cal/day),Body Fat (%)\n")
        records.sortedBy { it.timestamp }.forEach { r ->
            val weight = UnitFormatter.weightToDisplay(r.weight, unitSystem)
            val height = UnitFormatter.heightToDisplay(r.height, unitSystem)
            val bodyFat = r.bodyFatPercentage?.let { String.format(Locale.US, "%.1f", it) } ?: ""
            sb.append(dateFormat.format(Date(r.timestamp))).append(',')
                .append(String.format(Locale.US, "%.1f", weight)).append(',')
                .append(String.format(Locale.US, "%.1f", height)).append(',')
                .append(String.format(Locale.US, "%.1f", r.bmi)).append(',')
                .append(String.format(Locale.US, "%.0f", r.bmr)).append(',')
                .append(String.format(Locale.US, "%.0f", r.tdee)).append(',')
                .append(bodyFat).append('\n')
        }
        return sb.toString()
    }

    fun exportBmiRecords(context: Context, records: List<BmiRecord>, unitSystem: String): Uri? {
        if (records.isEmpty()) return null

        val dir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
        val file = File(dir, "bmi_history_${System.currentTimeMillis()}.csv")
        file.writeText(buildCsvContent(records, unitSystem))

        return FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
    }
}
