package com.samsunggalaxy.share

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.samsunggalaxy.utils.FILE_PROVIDER_AUTHORITY
import java.io.File
import java.io.FileOutputStream

/**
 * Idea I3 — Share Progress Card. Same FileProvider pattern as CsvExporter (app-external-files,
 * no WRITE_EXTERNAL_STORAGE needed).
 */
object ShareProgressCardExporter {
    fun save(context: Context, bitmap: Bitmap): Uri {
        val dir = File(context.getExternalFilesDir(null), "share_cards").apply { mkdirs() }
        // This feature is meant to be tapped repeatedly (unlike the one-off CSV export it
        // mirrors), so unlike CsvExporter's exports/ dir, old cards are cleaned up on every
        // save instead of accumulating indefinitely — only the just-shared image needs to exist.
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "progress_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        return FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
    }
}
