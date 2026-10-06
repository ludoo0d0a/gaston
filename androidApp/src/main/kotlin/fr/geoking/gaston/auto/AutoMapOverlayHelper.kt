package fr.geoking.gaston.auto

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import fr.geoking.gaston.aac.DangerZoneTriangle
import fr.geoking.gaston.poi.Poi
import kotlin.math.cos

object AutoMapOverlayHelper {

    /**
     * Directional danger-zone rectangles for radar amenity POIs (AFFTAC distances:
     * ~4 km / ~2 km / ~300 m). Near edge at the radar; far edge at the entry
     * boundary when a unidirectional bearing is known. Skips POIs with
     * missing/bidirectional direction.
     * [toScreenXy] maps each lat/lon to canvas coordinates.
     */
    fun drawRadarDangerZoneTriangles(
        canvas: Canvas,
        density: Float,
        radarPois: List<Poi>,
        toScreenXy: (lat: Double, lon: Double) -> Pair<Float, Float>,
    ) {
        if (radarPois.isEmpty()) return
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#22EF4444")
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EF4444")
            style = Paint.Style.STROKE
            strokeWidth = 3f * density
            strokeJoin = Paint.Join.ROUND
        }
        for (poi in radarPois) {
            val ring = DangerZoneTriangle.latLngRingForRadarPoi(poi) ?: continue
            if (ring.size < 4) continue
            val path = Path()
            ring.forEachIndexed { index, (lat, lon) ->
                val (x, y) = toScreenXy(lat, lon)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            canvas.drawPath(path, fillPaint)
            canvas.drawPath(path, strokePaint)
        }
    }

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

    /** Centered banner when an offline map file is required but missing. */
    fun drawOfflineUnavailableBanner(
        canvas: Canvas,
        context: android.content.Context,
        visibleArea: Rect?,
        surfaceWidth: Int,
        surfaceHeight: Int,
    ) {
        val area = visibleArea ?: Rect(0, 0, surfaceWidth, surfaceHeight)
        val density = context.resources.displayMetrics.density
        val message = context.getString(fr.geoking.gaston.R.string.map_offline_unavailable)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 14f * density
            textAlign = Paint.Align.CENTER
        }
        val bgPaint = Paint().apply {
            color = Color.argb(220, 40, 40, 40)
            style = Paint.Style.FILL
        }
        val padH = 16f * density
        val padV = 12f * density
        val maxWidth = area.width() * 0.85f
        val lines = wrapText(message, textPaint, maxWidth)
        val lineHeight = 18f * density
        val blockHeight = padV * 2 + lines.size * lineHeight
        val blockWidth = lines.maxOf { textPaint.measureText(it) } + padH * 2
        val left = area.centerX() - blockWidth / 2f
        val top = area.centerY() - blockHeight / 2f
        canvas.drawRoundRect(left, top, left + blockWidth, top + blockHeight, 8f * density, 8f * density, bgPaint)
        var y = top + padV + lineHeight * 0.75f
        for (line in lines) {
            canvas.drawText(line, area.centerX().toFloat(), y, textPaint)
            y += lineHeight
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(' ')
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) lines.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines.ifEmpty { listOf(text) }
    }

    fun drawCompassAndScale(
        canvas: Canvas,
        context: Context,
        visibleArea: Rect?,
        surfaceWidth: Int,
        surfaceHeight: Int,
        zoom: Float,
        latitude: Double,
        isDensityScaled: Boolean,
        modeLabel: String,
        dangerZoneHud: fr.geoking.gaston.radar.DangerZoneHudState? = null,
    ) {
        val density = context.resources.displayMetrics.density
        val area = visibleArea ?: Rect(0, 0, surfaceWidth, surfaceHeight)

        // Detect if content card/menu is on the right side of the screen
        val isMenuOnRight = (surfaceWidth - area.right) > area.left + (20 * density)

        // Compass lives in the ActionStrip; only draw scale + zoom chip on the map.
        drawScale(canvas, area, zoom, latitude, density, isDensityScaled, isMenuOnRight)
        drawZoomDebug(canvas, area, zoom, density, modeLabel)

        val hud = dangerZoneHud ?: currentDangerZoneHud()
        if (hud.active) {
            drawDangerZonePresenceBadge(
                canvas = canvas,
                context = context,
                area = area,
                density = density,
                speedLimitKmH = hud.speedLimitKmH,
                isMenuOnRight = isMenuOnRight,
            )
        }
    }

    private fun currentDangerZoneHud(): fr.geoking.gaston.radar.DangerZoneHudState {
        return try {
            org.koin.core.context.GlobalContext.getOrNull()
                ?.get<fr.geoking.gaston.radar.DangerZoneHudStore>()
                ?.hudState
                ?.value
                ?: fr.geoking.gaston.radar.DangerZoneHudState()
        } catch (_: Exception) {
            fr.geoking.gaston.radar.DangerZoneHudState()
        }
    }

    /**
     * AAC-compatible on-map presence chip: "Zone de danger" + EU VMA disk.
     * Drawn on the app map surface (MapWithContent) — allowed for POI apps;
     * not [androidx.car.app.AppManager.showAlert].
     */
    fun drawDangerZonePresenceBadge(
        canvas: Canvas,
        context: Context,
        area: Rect,
        density: Float,
        speedLimitKmH: Int?,
        isMenuOnRight: Boolean,
    ) {
        val zoneLabel = context.getString(fr.geoking.gaston.R.string.aac_hud_zone_entry)
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 13f * density
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val vmaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(230, 255, 255, 255)
            textSize = 11f * density
        }
        val vmaText = if (speedLimitKmH != null && speedLimitKmH > 0) {
            context.getString(fr.geoking.gaston.R.string.aac_hud_vma, speedLimitKmH)
        } else {
            null
        }

        val diskRadius = 22f * density
        val pad = 10f * density
        val labelWidth = labelPaint.measureText(zoneLabel)
        val vmaWidth = vmaText?.let { vmaPaint.measureText(it) } ?: 0f
        val textBlockWidth = maxOf(labelWidth, vmaWidth)
        val textBlockHeight = if (vmaText != null) 32f * density else 16f * density
        val showDisk = speedLimitKmH != null && speedLimitKmH > 0
        val contentWidth = textBlockWidth + (if (showDisk) pad + diskRadius * 2f else 0f)
        val contentHeight = maxOf(textBlockHeight, if (showDisk) diskRadius * 2f else 0f)
        val blockWidth = contentWidth + pad * 2
        val blockHeight = contentHeight + pad * 2

        val margin = 12f * density
        // Top-end of the map content area, opposite the list when possible.
        val left = if (isMenuOnRight) {
            area.left + margin
        } else {
            area.right - margin - blockWidth
        }
        val top = area.top + margin

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 120, 20, 20)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            left,
            top,
            left + blockWidth,
            top + blockHeight,
            10f * density,
            10f * density,
            bgPaint,
        )

        var textX = left + pad
        val textTop = top + pad
        canvas.drawText(zoneLabel, textX, textTop + 13f * density, labelPaint)
        if (vmaText != null) {
            canvas.drawText(vmaText, textX, textTop + 28f * density, vmaPaint)
        }

        if (showDisk && speedLimitKmH != null) {
            val cx = left + blockWidth - pad - diskRadius
            val cy = top + blockHeight / 2f
            drawEuSpeedLimitDisk(canvas, cx, cy, diskRadius, speedLimitKmH, density)
        }
    }

    /** EU B14-style speed limit disk for canvas (AA map surface). */
    fun drawEuSpeedLimitDisk(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        speedLimitKmH: Int,
        density: Float,
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E30613")
            style = Paint.Style.STROKE
            strokeWidth = radius * 0.22f
        }
        canvas.drawCircle(cx, cy, radius, fill)
        canvas.drawCircle(cx, cy, radius - rim.strokeWidth / 2f, rim)

        val number = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            textSize = radius * 0.95f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val text = speedLimitKmH.toString()
        val bounds = Rect()
        number.getTextBounds(text, 0, text.length, bounds)
        canvas.drawText(text, cx, cy - bounds.exactCenterY(), number)
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

    private fun drawScale(
        canvas: Canvas,
        area: Rect,
        zoom: Float,
        latitude: Double,
        density: Float,
        isDensityScaled: Boolean,
        isMenuOnRight: Boolean
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
        val x = if (isMenuOnRight) {
            area.right - margin - scaleWidthPx
        } else {
            area.left + margin
        }
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

    private fun drawZoomDebug(canvas: Canvas, area: Rect, zoom: Float, density: Float, modeLabel: String) {
        val margin = 16f * density
        val x = area.left + margin
        val y = area.top + margin

        val zoomText = String.format("Zoom: %.2f", zoom)
        val text = if (modeLabel.isBlank()) zoomText else "$modeLabel · $zoomText"

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
