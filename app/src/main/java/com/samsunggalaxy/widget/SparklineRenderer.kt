package com.samsunggalaxy.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/**
 * EPIC-09 T09.1 — RemoteViews can't run custom View drawing (no canvas access inside the
 * launcher's process), so the 7-day weight sparkline is pre-rendered to a Bitmap here and set
 * via `RemoteViews.setImageViewBitmap`.
 */
object SparklineRenderer {

    /** Pure — exposed for unit testing without touching Bitmap/Canvas. */
    fun normalize(values: List<Double>): List<Float> {
        if (values.isEmpty()) return emptyList()
        if (values.size == 1) return listOf(0.5f)
        val min = values.min()
        val max = values.max()
        val range = max - min
        return values.map { v -> if (range == 0.0) 0.5f else ((v - min) / range).toFloat() }
    }

    fun render(values: List<Double>, widthPx: Int, heightPx: Int, lineColor: Int): Bitmap? {
        if (values.size < 2 || widthPx <= 0 || heightPx <= 0) return null

        val normalized = normalize(values)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lineColor
            style = Paint.Style.STROKE
            strokeWidth = 4f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val verticalPadding = heightPx * 0.15f
        val usableHeight = heightPx - (verticalPadding * 2)
        val stepX = widthPx / (normalized.size - 1).toFloat()

        val path = android.graphics.Path()
        normalized.forEachIndexed { index, fraction ->
            val x = index * stepX
            // fraction=1 (max weight) draws at the top, so invert for screen Y-down coords.
            val y = verticalPadding + (1f - fraction) * usableHeight
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)

        return bitmap
    }
}
