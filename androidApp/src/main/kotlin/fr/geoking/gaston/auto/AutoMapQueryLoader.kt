package fr.geoking.gaston.auto

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

/**
 * Small indeterminate spinner drawn on the Android Auto map while a POI query is in flight.
 */
object AutoMapQueryLoader {
    private const val SIZE_PX = 28f
    private const val STROKE_PX = 3.5f
    private const val MARGIN_PX = 14f
    private const val PERIOD_MS = 900L
    private const val SWEEP_DEG = 270f

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = STROKE_PX
        strokeCap = Paint.Cap.ROUND
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = STROKE_PX
        strokeCap = Paint.Cap.ROUND
    }

    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(140, 30, 30, 30)
        style = Paint.Style.FILL
    }

    fun draw(
        canvas: Canvas,
        visibleArea: Rect?,
        surfaceWidth: Int,
        surfaceHeight: Int,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val area = visibleArea?.takeIf { it.width() > 0 && it.height() > 0 }
            ?: Rect(0, 0, surfaceWidth.coerceAtLeast(1), surfaceHeight.coerceAtLeast(1))
        val cx = area.right - MARGIN_PX - SIZE_PX / 2f
        val cy = area.top + MARGIN_PX + SIZE_PX / 2f
        val half = SIZE_PX / 2f
        canvas.drawCircle(cx, cy, half + 4f, discPaint)
        val oval = RectF(cx - half, cy - half, cx + half, cy + half)
        canvas.drawArc(oval, 0f, 360f, false, trackPaint)
        val start = ((nowMs % PERIOD_MS).toFloat() / PERIOD_MS) * 360f
        canvas.drawArc(oval, start, SWEEP_DEG, false, arcPaint)
    }
}
