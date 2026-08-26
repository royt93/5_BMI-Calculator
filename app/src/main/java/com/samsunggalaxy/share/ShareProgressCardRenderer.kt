package com.samsunggalaxy.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.drawable.toBitmap
import com.samsunggalaxy.R
import com.samsunggalaxy.utils.UnitFormatter

/**
 * Idea I3 — Share Progress Card. Renders a Spotify-Wrapped-style summary image ("30 days, -2.3kg,
 * 18 day streak" + mini trend line) for the user to share to social media. Pre-rendered to a
 * Bitmap with Canvas (same reasoning as widget/SparklineRenderer — no View hierarchy available
 * for the target, a file on disk).
 *
 * Bitmap/Canvas calls throw in a plain JVM unit test (no Robolectric in this project — see
 * SparklineRendererTest's precedent), so this is verified via on-device smoke test, not unit
 * tests. Pure formatting logic lives in CalculatorUtils.calculateWeightChange instead.
 */
object ShareProgressCardRenderer {
    private const val WIDTH = 1080
    private const val HEIGHT = 1350

    fun render(
        context: Context,
        periodDays: Int,
        deltaKg: Double?,
        unitSystem: String,
        streakDays: Int,
        sparklineValues: List<Double>
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Same gradient as activity_splash.xml's bg_splash_gradient, for brand consistency.
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(),
                intArrayOf(Color.parseColor("#4285F4"), Color.parseColor("#5E9EFF"), Color.parseColor("#2E5BFF")),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), backgroundPaint)

        val white = Color.WHITE
        val centerX = WIDTH / 2f

        // App icon, small and centered.
        val icon = androidx.core.content.ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.toBitmap(120, 120)
        if (icon != null) {
            canvas.drawBitmap(icon, centerX - 60f, 90f, Paint(Paint.ANTI_ALIAS_FLAG))
        }

        val brandPaint = textPaint(white, 32f, Paint.Align.CENTER, alpha = 200)
        canvas.drawText(context.getString(R.string.app_name), centerX, 260f, brandPaint)

        val periodPaint = textPaint(white, 36f, Paint.Align.CENTER, alpha = 180)
        canvas.drawText(context.getString(R.string.share_progress_period_label, periodDays), centerX, 330f, periodPaint)

        val deltaText = if (deltaKg != null) {
            UnitFormatter.formatSignedWeightDelta(deltaKg, unitSystem)
        } else {
            context.getString(R.string.share_progress_no_data)
        }
        val deltaPaint = textPaint(white, 140f, Paint.Align.CENTER, bold = true)
        canvas.drawText(deltaText, centerX, 560f, deltaPaint)

        if (streakDays > 0) {
            val streakPaint = textPaint(white, 48f, Paint.Align.CENTER, bold = true)
            canvas.drawText(context.getString(R.string.share_progress_streak_label, streakDays), centerX, 680f, streakPaint)
        }

        if (sparklineValues.size >= 2) {
            drawTrendLine(canvas, sparklineValues, top = 780f, bottom = 1080f)
        }

        val footerPaint = textPaint(white, 28f, Paint.Align.CENTER, alpha = 160)
        canvas.drawText(context.getString(R.string.share_progress_footer), centerX, HEIGHT - 80f, footerPaint)

        return bitmap
    }

    private fun textPaint(color: Int, size: Float, align: Paint.Align, bold: Boolean = false, alpha: Int = 255): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.alpha = alpha
            textSize = size
            textAlign = align
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun drawTrendLine(canvas: Canvas, values: List<Double>, top: Float, bottom: Float) {
        val min = values.min()
        val max = values.max()
        val range = max - min
        val marginX = 120f
        val usableWidth = WIDTH - (marginX * 2)
        val stepX = usableWidth / (values.size - 1)
        val usableHeight = bottom - top

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 8f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val path = Path()
        values.forEachIndexed { index, value ->
            val fraction = if (range == 0.0) 0.5f else ((value - min) / range).toFloat()
            val x = marginX + index * stepX
            val y = top + (1f - fraction) * usableHeight
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)
    }
}
