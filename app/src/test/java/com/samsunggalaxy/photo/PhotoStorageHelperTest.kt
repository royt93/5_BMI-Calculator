package com.samsunggalaxy.photo

import android.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Test

/** Idea I1 — Progress Photo Timeline: the pure parts of photo normalization. */
class PhotoStorageHelperTest {

    // ---- calculateSampleSize ----

    @Test
    fun calculateSampleSize_alreadySmallerThanMax_returnsOne() {
        assertEquals(1, PhotoStorageHelper.calculateSampleSize(800, 600, maxDimension = 1600))
    }

    @Test
    fun calculateSampleSize_exactlyMax_returnsOne() {
        assertEquals(1, PhotoStorageHelper.calculateSampleSize(1600, 1600, maxDimension = 1600))
    }

    @Test
    fun calculateSampleSize_doubleTheMax_returnsTwo() {
        assertEquals(2, PhotoStorageHelper.calculateSampleSize(3200, 2400, maxDimension = 1600))
    }

    @Test
    fun calculateSampleSize_quadrupleTheMax_returnsFour() {
        assertEquals(4, PhotoStorageHelper.calculateSampleSize(6400, 4800, maxDimension = 1600))
    }

    @Test
    fun calculateSampleSize_drivenByTheLongerSide_evenIfTheOtherIsSmall() {
        // A 6000x1000 panorama-shaped photo still needs downscaling — capped by the longer side.
        assertEquals(2, PhotoStorageHelper.calculateSampleSize(width = 6000, height = 1000, maxDimension = 1600))
    }

    // ---- orientationToDegrees ----

    @Test
    fun orientationToDegrees_normal_isZero() {
        assertEquals(0, PhotoStorageHelper.orientationToDegrees(ExifInterface.ORIENTATION_NORMAL))
    }

    @Test
    fun orientationToDegrees_rotate90_is90() {
        assertEquals(90, PhotoStorageHelper.orientationToDegrees(ExifInterface.ORIENTATION_ROTATE_90))
    }

    @Test
    fun orientationToDegrees_rotate180_is180() {
        assertEquals(180, PhotoStorageHelper.orientationToDegrees(ExifInterface.ORIENTATION_ROTATE_180))
    }

    @Test
    fun orientationToDegrees_rotate270_is270() {
        assertEquals(270, PhotoStorageHelper.orientationToDegrees(ExifInterface.ORIENTATION_ROTATE_270))
    }

    @Test
    fun orientationToDegrees_unknownFlippedValue_defaultsToZero() {
        // Mirrored orientations (FLIP_HORIZONTAL etc.) aren't handled by a plain rotation matrix
        // and fall back to 0 rather than guessing wrong.
        assertEquals(0, PhotoStorageHelper.orientationToDegrees(ExifInterface.ORIENTATION_FLIP_HORIZONTAL))
    }
}
