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
    private const val PERIOD_MS = 900L
    private const val SWEEP_DEG = 270f

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(140, 30, 30, 30)
        style = Paint.Style.FILL
    }

    fun draw(
        canvas: Canvas,
        density: Float,
        visibleArea: Rect?,
        surfaceWidth: Int,
        surfaceHeight: Int,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val area = visibleArea?.takeIf { it.width() > 0 && it.height() > 0 }
            ?: Rect(0, 0, surfaceWidth.coerceAtLeast(1), surfaceHeight.coerceAtLeast(1))

        // Above mapActionStrip zoom buttons (+ / −), hugging the map edge (former compass slot).
        val isMenuOnRight = (surfaceWidth - area.right) > area.left + (20 * density)
        val slotRadius = 28f * density
        val bottomMargin = 16f * density
        val edgeMargin = 4f * density
        val buttonSpacing = 8f * density
        val loaderRadius = 14f * density // 28dp diameter on screen

        val cy = area.bottom - bottomMargin - slotRadius - 2f * (slotRadius * 2f + buttonSpacing)
        val cx = if (isMenuOnRight) {
            area.left + edgeMargin + slotRadius
        } else {
            area.right - edgeMargin - slotRadius
        }

        // Dynamic stroke scaling based on density
        val strokeWidth = 3.5f * density
        trackPaint.strokeWidth = strokeWidth
        arcPaint.strokeWidth = strokeWidth

        // Draw background capsule disc
        canvas.drawCircle(cx, cy, loaderRadius + 4f * density, discPaint)

        // Draw track and animating arc
        val oval = RectF(cx - loaderRadius, cy - loaderRadius, cx + loaderRadius, cy + loaderRadius)
        canvas.drawArc(oval, 0f, 360f, false, trackPaint)

        val start = ((nowMs % PERIOD_MS).toFloat() / PERIOD_MS) * 360f
        canvas.drawArc(oval, start, SWEEP_DEG, false, arcPaint)
    }
}
