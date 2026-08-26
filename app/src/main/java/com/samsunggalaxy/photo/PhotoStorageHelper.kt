package com.samsunggalaxy.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import com.samsunggalaxy.utils.FILE_PROVIDER_AUTHORITY
import java.io.File
import java.io.FileOutputStream

/**
 * Idea I1 — Progress Photo Timeline. Photos live under this app's own external-files/photos/
 * dir — same FileProvider pattern as CsvExporter/ShareProgressCardExporter — never MediaStore,
 * never shared storage, matching the idea's own "chỉ lưu local, không upload cloud" commitment.
 * Every photo is orientation-corrected, downscaled, and re-encoded as JPEG on save; re-encoding
 * this way also strips EXIF metadata (including GPS tags) as a side effect, which matters here
 * since body-progress photos are sensitive data.
 */
object PhotoStorageHelper {
    private const val PHOTOS_DIR = "photos"
    private const val MAX_DIMENSION = 1600
    private const val THUMBNAIL_DIMENSION = 200
    private const val JPEG_QUALITY = 85

    // Internal storage (filesDir), not external-files-dir like CsvExporter/PdfReportExporter —
    // those are meant to be shared out via the share sheet; photos of the user's body are the
    // most sensitive data this app touches, so they get the more private location even though
    // both are technically app-private. Requires a FileProvider grant to hand the camera app a
    // writable content:// URI regardless of which storage this resolves to.
    private fun photosDir(context: Context): File =
        File(context.filesDir, PHOTOS_DIR).apply { mkdirs() }

    /** A fresh empty file + its FileProvider content:// URI, for the camera app to capture into. */
    fun createCaptureTarget(context: Context): Pair<File, Uri> {
        val file = File(photosDir(context), "photo_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
        return file to uri
    }

    /**
     * Normalizes an in-place camera capture (orientation-corrects + downscales + strips EXIF)
     * by decoding and re-writing over itself. Returns false (file left untouched) if it can't
     * be decoded — the caller should treat that as a failed capture.
     */
    fun normalizeInPlace(file: File): Boolean {
        val rotation = readRotationDegrees(file.absolutePath)
        val bitmap = decodeSampledFromFile(file.absolutePath)?.let { rotate(it, rotation) } ?: return false
        return try {
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }
            true
        } catch (e: Exception) {
            false
        } finally {
            bitmap.recycle()
        }
    }

    /** Copies a picked gallery image into this app's private storage (downscaled, EXIF-stripped). */
    fun copyFromUri(context: Context, sourceUri: Uri): File? {
        val rotation = readRotationDegrees(context, sourceUri)
        val bitmap = decodeSampledFromUri(context, sourceUri)?.let { rotate(it, rotation) } ?: return null
        val file = File(photosDir(context), "photo_${System.currentTimeMillis()}.jpg")
        return try {
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }
            file
        } catch (e: Exception) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    fun deletePhoto(path: String) {
        File(path).delete()
    }

    /**
     * Downsampled decode for grid/list thumbnails — photos are saved up to MAX_DIMENSION
     * (1600px); decoding that full size on every RecyclerView bind is unnecessary main-thread
     * work for a tiny thumbnail. Callers still decide which dispatcher to call this from.
     */
    fun decodeThumbnail(path: String, maxDimension: Int = THUMBNAIL_DIMENSION): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
        }
        return BitmapFactory.decodeFile(path, options)
    }

    /** Pure — exposed for unit testing. Largest power-of-2 downscale keeping the longer side >= maxDimension. */
    fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        val longestSide = maxOf(width, height)
        var sampleSize = 1
        while (longestSide / (sampleSize * 2) >= maxDimension) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun decodeSampledFromFile(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION)
        }
        return BitmapFactory.decodeFile(path, options)
    }

    private fun decodeSampledFromUri(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION)
        }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun readRotationDegrees(path: String): Int = try {
        orientationToDegrees(ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL))
    } catch (e: Exception) {
        0
    }

    private fun readRotationDegrees(context: Context, uri: Uri): Int = try {
        context.contentResolver.openInputStream(uri)?.use {
            orientationToDegrees(ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL))
        } ?: 0
    } catch (e: Exception) {
        0
    }

    /** Pure — exposed for unit testing. android.media.ExifInterface has no rotationDegrees convenience (that's androidx-only). */
    fun orientationToDegrees(exifOrientation: Int): Int = when (exifOrientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
}
