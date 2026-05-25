package fr.geoking.gaston.auto

import android.graphics.Rect
import fr.geoking.gaston.poi.Poi
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/**
 * Screen-space hit testing for POI markers on the Android Auto map surface.
 * Kept separate from [AutoSurfaceRenderer] so tap logic is unit-testable.
 */
internal object AutoMapPoiHitTest {

    /** Minimum tap radius in px (larger than drawn icon at low zoom). */
    const val MIN_HIT_RADIUS_PX = 56f

    fun screenToMapCoords(
        screenX: Float,
        screenY: Float,
        mapBearingDegrees: Float,
        centerPxX: Float,
        centerPxY: Float,
    ): Pair<Float, Float> {
        if (mapBearingDegrees == 0f) return screenX to screenY
        val angleRad = Math.toRadians(mapBearingDegrees.toDouble())
        val dx = screenX - centerPxX
        val dy = screenY - centerPxY
        val rx = dx * cos(angleRad) + dy * sin(angleRad)
        val ry = -dx * sin(angleRad) + dy * cos(angleRad)
        return (centerPxX + rx).toFloat() to (centerPxY + ry).toFloat()
    }

    fun poiScreenPosition(
        poi: Poi,
        mapLat: Double,
        mapLon: Double,
        zoom: Int,
        centerPxX: Double,
        centerPxY: Double,
    ): Pair<Float, Float> {
        val tileSize = 256
        val centerX = lonToTileX(mapLon, zoom)
        val centerY = latToTileY(mapLat, zoom)
        val tileX = lonToTileX(poi.longitude, zoom)
        val tileY = latToTileY(poi.latitude, zoom)
        val px = ((tileX - centerX) * tileSize + centerPxX).toFloat()
        val py = ((tileY - centerY) * tileSize + centerPxY).toFloat()
        return px to py
    }

    fun hitRadiusPx(markerWidthPx: Int): Float {
        return maxOf(markerWidthPx / 2f, MIN_HIT_RADIUS_PX)
    }

    /**
     * POIs whose marker bounds contain [screenX]/[screenY], sorted nearest-first.
     */
    fun findPoisAt(
        screenX: Float,
        screenY: Float,
        pois: List<Poi>,
        mapLat: Double,
        mapLon: Double,
        zoom: Int,
        mapBearingDegrees: Float,
        centerPxX: Double,
        centerPxY: Double,
        visibleArea: Rect?,
    ): List<Poi> {
        visibleArea?.let { area ->
            if (!area.contains(screenX.toInt(), screenY.toInt())) return emptyList()
        }

        val (worldX, worldY) = screenToMapCoords(
            screenX = screenX,
            screenY = screenY,
            mapBearingDegrees = mapBearingDegrees,
            centerPxX = centerPxX.toFloat(),
            centerPxY = centerPxY.toFloat(),
        )
        val markerWidthPx = markerWidthForZoom(zoom)
        val hitRadius = hitRadiusPx(markerWidthPx)
        val hitRadiusSq = hitRadius * hitRadius

        return pois.mapNotNull { poi ->
            val (px, py) = poiScreenPosition(poi, mapLat, mapLon, zoom, centerPxX, centerPxY)
            val dx = worldX - px
            val dy = worldY - py
            val distSq = dx * dx + dy * dy
            if (distSq <= hitRadiusSq) poi to distSq else null
        }
            .sortedBy { it.second }
            .map { it.first }
    }

    /** True when the two nearest hits overlap on screen and cannot be disambiguated by tap. */
    fun shouldZoomInsteadOfOpen(
        hits: List<Poi>,
        mapLat: Double,
        mapLon: Double,
        zoom: Int,
        centerPxX: Double,
        centerPxY: Double,
    ): Boolean {
        if (hits.size < 2) return false
        val (a, b) = hits[0] to hits[1]
        val (ax, ay) = poiScreenPosition(a, mapLat, mapLon, zoom, centerPxX, centerPxY)
        val (bx, by) = poiScreenPosition(b, mapLat, mapLon, zoom, centerPxX, centerPxY)
        val clusterDist = hypot((ax - bx).toDouble(), (ay - by).toDouble())
        val hitRadius = hitRadiusPx(markerWidthForZoom(zoom))
        return clusterDist < hitRadius * 1.5
    }

    private fun markerWidthForZoom(zoom: Int): Int {
        val baseWidth = AutoSurfaceRenderer.POI_MARKER_WIDTH_PX
        val baseZoom = 13
        val scale = 2.0.pow(zoom.toDouble() - baseZoom)
        return (baseWidth * scale).toInt().coerceIn(32, 256)
    }

    private fun lonToTileX(lon: Double, zoom: Int): Double =
        (lon + 180.0) / 360.0 * (1 shl zoom)

    private fun latToTileY(lat: Double, zoom: Int): Double {
        val latRad = Math.toRadians(lat)
        return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * (1 shl zoom)
    }

}
