package com.samsunggalaxy.photo

import android.util.Log
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.samsunggalaxy.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Idea I1 — shared "Add a progress photo" flow (take-photo/choose-gallery dialog, camera
 * capture, gallery pick, normalize/persist off the main thread). Used by both ResultAct (photo
 * on the just-saved weigh-in) and ProgressPhotosActivity (photo on a past weigh-in) — factored
 * out after the same MaterialAlertDialogBuilder quirk documented in [showCaptureSourceDialog]
 * had to be independently found and fixed at both call sites.
 *
 * Must be constructed in `onCreate` (or as a field initializer) — same before-STARTED
 * requirement as any other `registerForActivityResult` call.
 *
 * [onAttached] runs on [Dispatchers.IO] (same dispatcher [produceFile] runs on inside
 * [showCaptureSourceDialog]'s launchers) so it can do its own suspend DB write before
 * switching to Main for UI work; [onFailed] always runs on Main.
 */
class PhotoCaptureFlow(
    private val activity: AppCompatActivity,
    private val onAttached: suspend (File) -> Unit,
    private val onFailed: () -> Unit
) {
    private var pendingCameraFile: File? = null

    private val takePictureLauncher = activity.registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingCameraFile
        pendingCameraFile = null
        if (success && file != null) {
            persist { if (PhotoStorageHelper.normalizeInPlace(file)) file else null }
        }
    }

    private val pickPhotoLauncher = activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            persist { PhotoStorageHelper.copyFromUri(activity, uri) }
        }
    }

    /**
     * NOTE: `setMessage()` + `setItems()` on the same `MaterialAlertDialogBuilder` silently
     * drops the item list (confirmed on-device — title+message+Cancel renders, no way to pick
     * Take photo/Choose gallery). Explicit buttons instead, which DO coexist correctly with
     * `setMessage()`.
     */
    fun showCaptureSourceDialog(onCancel: () -> Unit) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.progress_photo_add_label))
            .setMessage(activity.getString(R.string.progress_photo_privacy_disclosure))
            .setPositiveButton(activity.getString(R.string.progress_photo_take_photo)) { _, _ -> launchCamera() }
            .setNeutralButton(activity.getString(R.string.progress_photo_choose_gallery)) { _, _ ->
                pickPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            .setNegativeButton(activity.getString(R.string.cancel)) { _, _ -> onCancel() }
            .show()
    }

    private fun launchCamera() {
        try {
            val (file, uri) = PhotoStorageHelper.createCaptureTarget(activity)
            pendingCameraFile = file
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            Log.e("roy93~", "launchCamera error", e)
            onFailed()
        }
    }

    private fun persist(produceFile: () -> File?) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file = produceFile()
                if (file != null) {
                    onAttached(file)
                } else {
                    withContext(Dispatchers.Main) { onFailed() }
                }
            } catch (e: Exception) {
                Log.e("roy93~", "photo persist error", e)
                withContext(Dispatchers.Main) { onFailed() }
            }
        }
    }
}
