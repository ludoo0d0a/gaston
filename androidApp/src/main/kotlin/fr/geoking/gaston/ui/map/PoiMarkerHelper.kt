package fr.geoking.gaston.ui.map

import android.content.Context
import android.graphics.*
import android.util.LruCache
import kotlin.math.pow
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import fr.geoking.gaston.R
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.MapPoiFilter
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.ui.BrandHelper
import fr.geoking.gaston.ui.ColorHelper

object PoiMarkerHelper {

    private val cache = LruCache<String, Bitmap>(100)

    /** Rasterized vector heads (rounded = circle + logo); keyed by id + bucketed pixel size. Do not recycle evicted entries (bitmaps may still be referenced by marker bitmaps in flight). */
    private val vectorRasterCache = LruCache<String, Bitmap>(150)

    fun clearCache() {
        synchronized(cache) {
            cache.evictAll()
        }
        synchronized(vectorRasterCache) {
            vectorRasterCache.evictAll()
        }
    }

    /** Bump when marker layout changes so [cache] entries are not stale. */
    private const val MARKER_LAYOUT_CACHE_TAG = "pinColumn11"

    /**
     * Builds a column marker bitmap: optional label pill (top) → rounded head (circle + logo in asset) → triangle pin (bottom).
     * The pin tip is at the bottom center (for default map anchor u=0.5, v=1).
     *
     * @param sizePx base width in px; height is derived from the layout.
     * @param markerStyle reserved for future pin layout variants (does not change rendering yet).
     */
    fun getMarkerBitmap(
        context: Context,
        poi: Poi,
        effectiveEnergyTypes: Set<String>,
        effectivePowerLevels: Set<Int>,
        isSelected: Boolean = false,
        isCheapest: Boolean = false,
        sizePx: Int = 120,
        availability: StationAvailabilitySummary? = null,
        markerStyle: MarkerStyle = MarkerStyle.Circle
    ): Bitmap {
        when (markerStyle) {
            MarkerStyle.Circle,
            MarkerStyle.Bubble -> Unit
        }
        val label = getPoiLabel(poi, effectiveEnergyTypes, effectivePowerLevels)
        val brandInfo = BrandHelper.getBrandInfo(poi.brand)
        val headDrawableId = headDrawableResId(poi, brandInfo)
        val category = poi.poiCategory ?: if (poi.isElectric) PoiCategory.Irve else PoiCategory.Gas
        val categoryColor = getPoiColor(poi, category, effectiveEnergyTypes, effectivePowerLevels)

        val cacheKey = "${poi.id}_${label}_${headDrawableId}_${categoryColor}_${isSelected}_${isCheapest}_${sizePx}_$MARKER_LAYOUT_CACHE_TAG"
        synchronized(cache) {
            cache.get(cacheKey)?.let { return it }
        }

        val w = sizePx.coerceIn(56, 512)
        val fillColor = categoryColor
        val labelFg = contrastingForegroundArgb(fillColor)
        val edgeStrokeArgb = if (isCheapest) 0xFFFFD700.toInt() else contrastingEdgeStrokeArgb(labelFg)
        val strokeW = if (isCheapest) (w * 0.07f).coerceIn(4f, 10f) else (w * 0.035f).coerceIn(2f, 5f)
        val topMargin = w * 0.03f
        val gapSmall = w * 0.025f

        val labelTextSize = w * 0.38f
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = labelFg
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = labelTextSize
        }

        var labelBlockH = 0f
        var labelRect = RectF()
        var labelBaseline = 0f
        if (!label.isNullOrEmpty()) {
            var ts = labelTextSize
            for (i in 0 until 12) {
                textPaint.textSize = ts
                if (textPaint.measureText(label) <= w - w * 0.10f) break
                ts *= 0.88f
            }
            val fm = textPaint.fontMetrics
            val textH = fm.descent - fm.ascent
            val padH = w * 0.08f
            val padV = (textH * 0.22f).coerceIn(w * 0.04f, w * 0.12f)
            val tw = textPaint.measureText(label)
            val rw = (tw + padH * 2).coerceAtMost(w - w * 0.06f)
            val rh = textH + padV * 2
            val rx = (w - rw) / 2f
            val ry = topMargin
            labelRect = RectF(rx, ry, rx + rw, ry + rh)
            val corner = w * 0.08f
            labelBaseline = ry + padV - fm.ascent
            labelBlockH = topMargin + rh + gapSmall
        } else {
            labelBlockH = topMargin
        }

        val circleR = w * 0.42f
        val circleCx = w / 2f
        val circleCy = labelBlockH + circleR
        val triH = w * 0.30f
        val overlap = circleR * 0.14f
        val triTopY = circleCy + circleR - overlap
        // Triangle top width ~ circle diameter so the pin reads aligned with the head.
        val triHalfW = circleR * 0.92f

        val h = (triTopY + triH + 2f).toInt().coerceAtLeast(w)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            isDither = true
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeW
            this.color = edgeStrokeArgb
            isDither = true
        }

        // 1) Triangle pin (behind head)
        val tipX = w / 2f
        val tipY = h - 1f
        val triPath = Path().apply {
            moveTo(tipX, tipY)
            lineTo(tipX - triHalfW, triTopY)
            lineTo(tipX + triHalfW, triTopY)
            close()
        }
        fillPaint.color = fillColor
        canvas.drawPath(triPath, fillPaint)
        canvas.drawPath(triPath, strokePaint)

        // 2) Pin head: *_rounded / brand roundedIconResId already include ic_poi_background_circle — no extra drawCircle.
        val headSizePx = (2f * circleR).toInt().coerceAtLeast(16)
        val headBitmap = vectorToBitmapCached(context, headDrawableId, headSizePx)
        if (headBitmap != null) {
            val left = circleCx - headBitmap.width / 2f
            val top = circleCy - headBitmap.height / 2f
            canvas.drawBitmap(headBitmap, left, top, null)
        }

        // 3) Label pill (optional, on top) — same fill as triangle; text/stroke contrast from [fillColor].
        if (!label.isNullOrEmpty() && labelRect.width() > 0f) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = fillColor
            }
            val labelStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = strokeW * 0.75f
                color = edgeStrokeArgb
            }
            val corner = w * 0.08f
            canvas.drawRoundRect(labelRect, corner, corner, bgPaint)
            canvas.drawRoundRect(labelRect, corner, corner, labelStroke)
            canvas.drawText(label, labelRect.centerX(), labelBaseline, textPaint)
        }

        synchronized(cache) {
            cache.put(cacheKey, bitmap)
        }
        return bitmap
    }

    /** WCAG-style relative luminance (sRGB), 0..1. */
    private fun relativeLuminance(argb: Int): Double {
        fun channel(c: Int): Double {
            val cs = c / 255.0
            return if (cs <= 0.03928) cs / 12.92 else ((cs + 0.055) / 1.055).pow(2.4)
        }
        val r = channel(Color.red(argb))
        val g = channel(Color.green(argb))
        val b = channel(Color.blue(argb))
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /** Black or white for readable text on [backgroundArgb] (opaque). */
    private fun contrastingForegroundArgb(backgroundArgb: Int): Int {
        return if (relativeLuminance(backgroundArgb) > 0.45) Color.BLACK else Color.WHITE
    }

    /** Outline that reads on top of the category fill (pairs with [contrastingForegroundArgb]). */
    private fun contrastingEdgeStrokeArgb(foregroundArgb: Int): Int {
        return if (foregroundArgb == Color.WHITE) {
            Color.argb(235, 255, 255, 255)
        } else {
            Color.argb(215, 28, 30, 34)
        }
    }

    private fun formatIrvePowerLabel(powerKw: Double): String {
        val k = powerKw.toInt()
        return "${k}kW"
    }

    internal fun getPoiLabel(
        poi: Poi,
        effectiveEnergyTypes: Set<String>,
        effectivePowerLevels: Set<Int>
    ): String? {
        val category = poi.poiCategory ?: if (poi.isElectric) PoiCategory.Irve else PoiCategory.Gas
        val hasElectricInFilter = effectiveEnergyTypes.contains("electric")
        val hasPowerFilter = effectivePowerLevels.isNotEmpty()
        val fuelIds = effectiveEnergyTypes - "electric"
        val hasFuelFilter = fuelIds.isNotEmpty()

        val isHybrid = poi.isElectric && !poi.fuelPrices.isNullOrEmpty()
        val hasAnyIrveFilter = hasElectricInFilter || hasPowerFilter

        // Priority 1: Fuel
        if (hasFuelFilter && (category == PoiCategory.Gas || isHybrid)) {
            val prices = poi.fuelPrices
            if (!prices.isNullOrEmpty()) {
                val matchingPrices = prices.filter { !it.outOfStock && MapPoiFilter.fuelNameToId(it.fuelName) in fuelIds }
                val bestPrice = matchingPrices.minByOrNull { it.price }?.price
                if (bestPrice != null) return "€%.2f".format(bestPrice)
            }
        }

        // Priority 2: IRVE
        if (hasAnyIrveFilter && (category == PoiCategory.Irve || isHybrid)) {
            val power = poi.powerKw
            if (power != null) {
                val matches = (hasElectricInFilter && !hasPowerFilter) || (hasPowerFilter && MapPoiFilter.powerMatchesAnyLevel(power, effectivePowerLevels))
                if (matches) {
                    return formatIrvePowerLabel(power)
                }
            }
        }

        return when (category) {
            PoiCategory.Radar -> {
                val regex = Regex("""(\d+)""")
                regex.find(poi.name)?.value?.let { "$it" }
            }
            else -> null
        }
    }

    internal fun getPoiColor(
        poi: Poi,
        category: PoiCategory,
        effectiveEnergyTypes: Set<String>,
        effectivePowerLevels: Set<Int>
    ): Int {
        val hasElectricInFilter = effectiveEnergyTypes.contains("electric")
        val hasPowerFilter = effectivePowerLevels.isNotEmpty()
        val fuelIds = effectiveEnergyTypes - "electric"
        val hasFuelFilter = fuelIds.isNotEmpty()

        val isHybrid = poi.isElectric && !poi.fuelPrices.isNullOrEmpty()
        val hasAnyIrveFilter = hasElectricInFilter || hasPowerFilter

        // Priority 1: Fuel
        if (category == PoiCategory.Gas || (isHybrid && hasFuelFilter)) {
            if (fuelIds.size == 1) {
                val color = ColorHelper.getFuelColor(fuelIds.first())
                if (color != null) return color.toArgb()
            }
            if (category == PoiCategory.Gas) return 0xFF007BFF.toInt() // Default Gas blue
        }

        // Priority 2: IRVE
        if (category == PoiCategory.Irve || (isHybrid && hasAnyIrveFilter)) {
            val power = poi.powerKw
            if (power != null) {
                val matches = (hasElectricInFilter && !hasPowerFilter) || (hasPowerFilter && MapPoiFilter.powerMatchesAnyLevel(power, effectivePowerLevels))
                if (matches) {
                    return ColorHelper.getPowerColor(power).toArgb()
                }
            }
            return 0xFF28A745.toInt() // Default IRVE green
        }

        return when (category) {
            PoiCategory.Radar -> 0xFFDC3545.toInt()
            PoiCategory.Parking -> 0xFF007BFF.toInt() // Parking blue
            else -> 0xFF17A2B8.toInt()
        }
    }

    /**
     * Layer-list drawables with built-in disc ([BrandHelper.BrandInfo.roundedIconResId] or [ic_poi_*_rounded]).
     */
    private fun headDrawableResId(poi: Poi, brandInfo: BrandHelper.BrandInfo?): Int {
        if (brandInfo != null) return brandInfo.roundedIconResId
        return when (poi.poiCategory) {
            PoiCategory.Toilet -> R.drawable.ic_poi_toilet_rounded
            PoiCategory.DrinkingWater -> R.drawable.ic_poi_water_rounded
            PoiCategory.Camping -> R.drawable.ic_poi_camping_rounded
            PoiCategory.CaravanSite -> R.drawable.ic_poi_caravan_rounded
            PoiCategory.PicnicSite -> R.drawable.ic_poi_picnic_rounded
            PoiCategory.Radar -> R.drawable.ic_poi_radar_rounded
            PoiCategory.Parking -> R.drawable.ic_poi_parking_rounded
            else -> if (poi.isElectric) R.drawable.ic_poi_electric_rounded else R.drawable.ic_poi_gas_rounded
        }
    }

    private fun vectorToBitmapCached(context: Context, drawableId: Int, size: Int): Bitmap? {
        val bucket = (size + 7) / 8 * 8
        val key = "$drawableId-$bucket"
        synchronized(vectorRasterCache) {
            vectorRasterCache.get(key)?.let { return it }
        }
        val created = vectorToBitmap(context, drawableId, bucket) ?: return null
        synchronized(vectorRasterCache) {
            vectorRasterCache.get(key)?.let { return it }
            vectorRasterCache.put(key, created)
        }
        return created
    }

    private fun vectorToBitmap(context: Context, drawableId: Int, size: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(context, drawableId)?.mutate() ?: return null
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
    }
}
