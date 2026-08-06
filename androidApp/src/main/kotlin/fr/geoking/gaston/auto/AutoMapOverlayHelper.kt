package fr.geoking.gaston.auto

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.cos

object AutoMapOverlayHelper {

    fun drawCompassAndScale(
        canvas: Canvas,
        context: Context,
        visibleArea: Rect?,
        surfaceWidth: Int,
        surfaceHeight: Int,
        bearing: Float,
        zoom: Float,
        latitude: Double,
        mapTileDebugEnabled: Boolean
    ) {
        val density = context.resources.displayMetrics.density
        val area = visibleArea ?: Rect(0, 0, surfaceWidth, surfaceHeight)

        // 1. Draw Compass (Top-Right of visible area)
        drawCompass(canvas, area, bearing, density)

        // 2. Draw Scale (Bottom-Left of visible area)
        drawScale(canvas, area, zoom, latitude, density)

        // 3. Draw Zoom debug layer if enabled (Top-Left of visible area, plus global, plus gros)
        if (mapTileDebugEnabled) {
            drawZoomDebug(canvas, area, zoom, density)
        }
    }

    private fun drawCompass(canvas: Canvas, area: Rect, bearing: Float, density: Float) {
        val compassRadius = 20f * density
        val margin = 16f * density

        val cx = area.right - margin - compassRadius
        val cy = area.top + margin + compassRadius

        val needleLength = compassRadius * 0.8f
        val needleWidth = compassRadius * 0.5f

        canvas.save()
        canvas.rotate(-bearing, cx, cy)

        // Draw background circle
        val bgPaint = Paint().apply {
            isAntiAlias = true
            color = Color.argb(128, 0, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, compassRadius, bgPaint)

        val borderPaint = Paint().apply {
            isAntiAlias = true
            color = Color.argb(128, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
        }
        canvas.drawCircle(cx, cy, compassRadius, borderPaint)

        // Red triangle (North pointer)
        val redPath = Path().apply {
            moveTo(cx, cy - needleLength)
            lineTo(cx - needleWidth / 2, cy)
            lineTo(cx + needleWidth / 2, cy)
            close()
        }
        val redPaint = Paint().apply {
            isAntiAlias = true
            color = Color.RED
            style = Paint.Style.FILL
        }
        canvas.drawPath(redPath, redPaint)

        // White triangle (South pointer)
        val whitePath = Path().apply {
            moveTo(cx, cy + needleLength)
            lineTo(cx - needleWidth / 2, cy)
            lineTo(cx + needleWidth / 2, cy)
            close()
        }
        val whitePaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawPath(whitePath, whitePaint)

        // Draw outline around needles for contrast
        val outlinePaint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
        }
        canvas.drawPath(redPath, outlinePaint)
        canvas.drawPath(whitePath, outlinePaint)

        // Draw center pivot
        val pivotPaint = Paint().apply {
            isAntiAlias = true
            color = Color.DKGRAY
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, 3f * density, pivotPaint)
        pivotPaint.color = Color.WHITE
        canvas.drawCircle(cx, cy, 1.5f * density, pivotPaint)

        canvas.restore()
    }

    private fun drawScale(canvas: Canvas, area: Rect, zoom: Float, latitude: Double, density: Float) {
        // Standard Mercator projection calculation
        val metersPerPixel = 156543.03392 * cos(Math.toRadians(latitude)) / Math.pow(2.0, zoom.toDouble())

        // Target scale length on screen: about 80dp
        val targetWidthPx = 80f * density
        val targetMeters = targetWidthPx * metersPerPixel

        val distances = doubleArrayOf(
            1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0,
            1000.0, 2000.0, 5000.0, 10000.0, 20000.0, 50000.0, 100000.0, 200000.0, 500000.0
        )
        val selectedDistance = distances.minByOrNull { Math.abs(it - targetMeters) } ?: 100.0
        val scaleWidthPx = (selectedDistance / metersPerPixel).toFloat()

        val distanceText = if (selectedDistance >= 1000.0) {
            "${(selectedDistance / 1000.0).toInt()} km"
        } else {
            "${selectedDistance.toInt()} m"
        }

        val margin = 16f * density
        val x = area.left + margin
        val y = area.bottom - margin

        // Draw a dark background capsule
        val bgRect = RectF(
            x - 6 * density,
            y - 24 * density,
            x + scaleWidthPx + 6 * density,
            y + 6 * density
        )
        val bgPaint = Paint().apply {
            isAntiAlias = true
            color = Color.argb(128, 0, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(bgRect, 4 * density, 4 * density, bgPaint)

        // Draw text
        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textSize = 10f * density
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(distanceText, x + scaleWidthPx / 2f, y - 10 * density, textPaint)

        // Draw scale line and ticks
        val linePaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
        }
        canvas.drawLine(x, y, x + scaleWidthPx, y, linePaint)
        canvas.drawLine(x, y - 4 * density, x, y + 4 * density, linePaint)
        canvas.drawLine(x + scaleWidthPx, y - 4 * density, x + scaleWidthPx, y + 4 * density, linePaint)
    }

    private fun drawZoomDebug(canvas: Canvas, area: Rect, zoom: Float, density: Float) {
        val margin = 16f * density
        val x = area.left + margin
        val y = area.top + margin

        val text = String.format("Zoom: %.2f", zoom)

        // Set up paint for drawing text
        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.YELLOW
            textSize = 20f * density // 20dp is nice and large ("plus gros")
            style = Paint.Style.FILL
            isUnderlineText = false
        }

        // Measure text for background rect
        val bounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, bounds)

        val padding = 8f * density
        val bgRect = RectF(
            x - padding,
            y - padding,
            x + bounds.width() + padding,
            y + bounds.height() + padding
        )

        val bgPaint = Paint().apply {
            isAntiAlias = true
            color = Color.argb(200, 0, 0, 0) // Highly visible dark background
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(bgRect, 4 * density, 4 * density, bgPaint)

        canvas.drawText(text, x, y + bounds.height(), textPaint)
    }
}
