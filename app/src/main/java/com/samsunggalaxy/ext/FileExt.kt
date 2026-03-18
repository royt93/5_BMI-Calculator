package com.samsunggalaxy.ext

import android.app.Activity
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

// BUG-09: Removed top-level DEFAULT_FILENAME — timestamp was evaluated once at class load.
// Now computed per call via default parameter.
fun saveBitmap(
    activity: Activity,
    bitmap: Bitmap,
    filename: String = "BMI Calculator ${System.currentTimeMillis()}",
): Uri? {
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }
    }

    val contentResolver = activity.contentResolver

    val imageUri: Uri? = contentResolver.insert(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    )

    return imageUri.also {
        val fileOutputStream = imageUri?.let { contentResolver.openOutputStream(it) }
        fileOutputStream?.let {
            bitmap.compress(
                /* format = */ Bitmap.CompressFormat.JPEG,
                /* quality = */ 100,
                /* stream = */ it
            )
        }
        fileOutputStream?.close()
    }
}
