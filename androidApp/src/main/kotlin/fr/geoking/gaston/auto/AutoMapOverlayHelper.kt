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

    enum class MapLibreStatusSeverity {
        Ok,
        Pending,
        Error,
    }

    data class MapLibreStatusChip(
        val title: String,
        val subtitle: String,
        val severity: MapLibreStatusSeverity = MapLibreStatusSeverity.Pending,
    )

    /** Compact always-on MapLibre AA pipeline status (surface / snapshot / canvas). */
    fun drawMapLibreStatusStrip(
        canvas: Canvas,
        visibleArea: Rect?,
        surfaceWidth: Int,
        surfaceHeight: Int,
        density: Float,
        chip: MapLibreStatusChip,
    ) {
        val area = visibleArea ?: Rect(0, 0, surfaceWidth, surfaceHeight)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 11f * density
            typeface = android.graphics.Typeface.MONOSPACE
            isFakeBoldText = true
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(230, 255, 255, 255)
            textSize = 10f * density
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val bgColor = when (chip.severity) {
            MapLibreStatusSeverity.Ok -> Color.argb(210, 20, 96, 48)
            MapLibreStatusSeverity.Pending -> Color.argb(210, 120, 84, 0)
            MapLibreStatusSeverity.Error -> Color.argb(210, 140, 24, 24)
        }
        val bgPaint = Paint().apply {
            color = bgColor
            style = Paint.Style.FILL
        }
        val pad = 6f * density
        val lineHeight = 13f * density
        val titleWidth = titlePaint.measureText(chip.title)
        val subtitleWidth = subtitlePaint.measureText(chip.subtitle)
        val blockWidth = maxOf(titleWidth, subtitleWidth) + pad * 2
        val blockHeight = pad * 2 + lineHeight * 2
        val margin = 8f * density
        val left = area.right - margin - blockWidth
        val top = area.bottom - margin - blockHeight
        canvas.drawRect(left, top, left + blockWidth, top + blockHeight, bgPaint)
        canvas.drawText(chip.title, left + pad, top + pad + lineHeight * 0.85f, titlePaint)
        canvas.drawText(chip.subtitle, left + pad, top + pad + lineHeight * 1.85f, subtitlePaint)
    }

    fun drawCompassAndScale(
        canvas: Canvas,
        context: Context,
        visibleArea: Rect?,
        surfaceWidth: Int,
        surfaceHeight: Int,
        bearing: Float,
        zoom: Float,
        latitude: Double,
        mapTileDebugEnabled: Boolean,
        isDensityScaled: Boolean
    ) {
        val density = context.resources.displayMetrics.density
        val area = visibleArea ?: Rect(0, 0, surfaceWidth, surfaceHeight)

        // 1. Draw Compass (Top-Right of visible area)
        drawCompass(canvas, area, bearing, density)

        // 2. Draw Scale (Bottom-Left of visible area)
        drawScale(canvas, area, zoom, latitude, density, isDensityScaled)

        // 3. Draw Zoom debug layer if enabled (Top-Left of visible area, plus global, plus gros)
        if (mapTileDebugEnabled) {
            drawZoomDebug(canvas, area, zoom, density)
        }
    }

    /** Extra diagnostic lines under the zoom debug chip (MapLibre AA / tile debug). */
    fun drawDebugHud(
        canvas: Canvas,
        context: Context,
        visibleArea: Rect?,
        surfaceWidth: Int,
        surfaceHeight: Int,
        lines: List<String>,
    ) {
        if (lines.isEmpty()) return
        val density = context.resources.displayMetrics.density
        val area = visibleArea ?: Rect(0, 0, surfaceWidth, surfaceHeight)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 11f * density
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val bgPaint = Paint().apply {
            color = Color.argb(180, 0, 0, 0)
            style = Paint.Style.FILL
        }
        val pad = 6f * density
        val lineHeight = 14f * density
        val blockHeight = pad * 2 + lines.size * lineHeight
        val maxWidth = lines.maxOfOrNull { textPaint.measureText(it) } ?: 0f
        val left = area.left + 8f * density
        // Below the large zoom chip
        val top = area.top + 48f * density
        canvas.drawRect(left, top, left + maxWidth + pad * 2, top + blockHeight, bgPaint)
        var y = top + pad + lineHeight * 0.8f
        for (line in lines) {
            canvas.drawText(line, left + pad, y, textPaint)
            y += lineHeight
        }
    }

    private fun drawCompass(canvas: Canvas, area: Rect, bearing: Float, density: Float) {
        val compassRadius = 24f * density
        val margin = 16f * density

        val cx = area.right - margin - compassRadius
        val cy = area.top + margin + compassRadius

        val needleLength = compassRadius * 0.65f
        val needleWidth = compassRadius * 0.38f

        canvas.save()
        canvas.rotate(-bearing, cx, cy)

        // Draw background circle with glassmorphic dark background
        val bgPaint = Paint().apply {
            isAntiAlias = true
            color = Color.argb(180, 20, 20, 20)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, compassRadius, bgPaint)

        // Draw thin inner concentric circle dial
        val dialPaint = Paint().apply {
            isAntiAlias = true
            color = Color.argb(50, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 0.8f * density
        }
        canvas.drawCircle(cx, cy, compassRadius * 0.88f, dialPaint)

        // Draw stylish high-contrast outer border
        val borderPaint = Paint().apply {
            isAntiAlias = true
            color = Color.argb(160, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
        }
        canvas.drawCircle(cx, cy, compassRadius, borderPaint)

        // Draw elegant bold "N" letter pointing north
        val nPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textSize = 7.5f * density
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("N", cx, cy - compassRadius + 9.5f * density, nPaint)

        // Red triangle (North pointer - Left half, bright red)
        val redLeftPath = Path().apply {
            moveTo(cx, cy - needleLength)
            lineTo(cx - needleWidth / 2, cy)
            lineTo(cx, cy)
            close()
        }
        val redLeftPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(255, 59, 48)
            style = Paint.Style.FILL
        }
        canvas.drawPath(redLeftPath, redLeftPaint)

        // Red triangle (North pointer - Right half, shaded red)
        val redRightPath = Path().apply {
            moveTo(cx, cy - needleLength)
            lineTo(cx + needleWidth / 2, cy)
            lineTo(cx, cy)
            close()
        }
        val redRightPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(201, 31, 31)
            style = Paint.Style.FILL
        }
        canvas.drawPath(redRightPath, redRightPaint)

        // White triangle (South pointer - Left half, bright white)
        val whiteLeftPath = Path().apply {
            moveTo(cx, cy + needleLength)
            lineTo(cx - needleWidth / 2, cy)
            lineTo(cx, cy)
            close()
        }
        val whiteLeftPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawPath(whiteLeftPath, whiteLeftPaint)

        // White triangle (South pointer - Right half, shaded silver/grey)
        val whiteRightPath = Path().apply {
            moveTo(cx, cy + needleLength)
            lineTo(cx + needleWidth / 2, cy)
            lineTo(cx, cy)
            close()
        }
        val whiteRightPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(211, 211, 211)
            style = Paint.Style.FILL
        }
        canvas.drawPath(whiteRightPath, whiteRightPaint)

        // Draw fine outline around needles for perfect contrast
        val outlinePaint = Paint().apply {
            isAntiAlias = true
            color = Color.argb(120, 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = 0.8f * density
        }
        canvas.drawPath(redLeftPath, outlinePaint)
        canvas.drawPath(redRightPath, outlinePaint)
        canvas.drawPath(whiteLeftPath, outlinePaint)
        canvas.drawPath(whiteRightPath, outlinePaint)

        // Draw stylish metallic center pivot cap
        val pivotPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(180, 180, 180)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, 3.5f * density, pivotPaint)
        pivotPaint.color = Color.WHITE
        canvas.drawCircle(cx, cy, 1.5f * density, pivotPaint)

        canvas.restore()
    }

    private fun drawScale(
        canvas: Canvas,
        area: Rect,
        zoom: Float,
        latitude: Double,
        density: Float,
        isDensityScaled: Boolean
    ) {
        // Standard Mercator projection calculation (meters per coordinate pixel / DP)
        val metersPerPixel = 156543.03392 * cos(Math.toRadians(latitude)) / Math.pow(2.0, zoom.toDouble()) * (256.0 / AutoSurfaceRenderer.TILE_SIZE)

        val metersPerPixelOnScreen = if (isDensityScaled) metersPerPixel / density else metersPerPixel

        // Target scale length on screen: about 80dp
        val targetWidthPx = 80f * density
        val targetMeters = targetWidthPx * metersPerPixelOnScreen

        val distances = doubleArrayOf(
            1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0,
            1000.0, 2000.0, 5000.0, 10000.0, 20000.0, 50000.0, 100000.0, 200000.0, 500000.0
        )
        val selectedDistance = distances.minByOrNull { Math.abs(it - targetMeters) } ?: 100.0
        val scaleWidthPx = (selectedDistance / metersPerPixelOnScreen).toFloat()

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
