package fr.geoking.gaston.auto

import fr.geoking.gaston.poi.MapPoiFilter
import fr.geoking.gaston.poi.Poi
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * Computes map center and zoom for Android Auto custom map surfaces (Web Mercator tiles).
 */
object AutoMapCamera {

    const val DEFAULT_ZOOM = 14
    const val MIN_ZOOM = 4
    const val MAX_ZOOM = 18
    const val MAP_FOCUS_STATION_COUNT = 2

    /**
     * Nearby search radius when [fr.geoking.gaston.poi.PoiSearchRequest.viewport] is null
     * (matches [fr.geoking.gaston.poi.SelectorPoiProvider] default).
     */
    const val DEFAULT_NEARBY_SEARCH_RADIUS_KM = 10

    /** Web Mercator meters-per-pixel at [lat] / integer [zoom]. */
    fun metersPerPixel(lat: Double, zoom: Int): Double {
        val z = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        return 156543.03392 * cos(lat * PI / 180.0).coerceIn(0.01, 1.0) / (1 shl z)
    }

    /** Screen-pixel radius for a geographic circle of [radiusKm] at [lat] / [zoom]. */
    fun radiusPxForKm(lat: Double, zoom: Int, radiusKm: Double): Float {
        if (radiusKm <= 0.0) return 0f
        val mpp = metersPerPixel(lat, zoom).coerceAtLeast(1e-6)
        return ((radiusKm * 1000.0) / mpp).toFloat()
    }

    /**
     * Closed ring of (lat, lon) approximating a circle of [radiusKm] around [centerLat]/[centerLon].
     */
    fun circleLatLngRing(
        centerLat: Double,
        centerLon: Double,
        radiusKm: Double,
        steps: Int = 64,
    ): List<Pair<Double, Double>> {
        if (radiusKm <= 0.0 || steps < 3) return emptyList()
        val cosLat = cos(centerLat * PI / 180.0).coerceIn(0.01, 1.0)
        val dLat = radiusKm / 111.0
        val dLon = radiusKm / (111.0 * cosLat)
        return (0..steps).map { i ->
            val bearing = 2.0 * PI * i / steps
            (centerLat + dLat * cos(bearing)) to (centerLon + dLon * sin(bearing))
        }
    }

    data class Camera(
        val centerLat: Double,
        val centerLon: Double,
        val zoom: Int,
    )

    /**
     * Picks up to [maxCount] stations to frame on the map (nearest first, or cheapest when [sortByPrice]).
     */
    fun selectMapFocusStations(
        userLat: Double,
        userLon: Double,
        stations: List<Poi>,
        maxCount: Int = MAP_FOCUS_STATION_COUNT,
        sortByPrice: Boolean = false,
        selectedFuelIds: Set<String> = emptySet(),
    ): List<Poi> {
        if (stations.isEmpty()) return emptyList()
        return MapPoiFilter.sortPois(
            pois = stations,
            lat = userLat,
            lon = userLon,
            sortByPrice = sortByPrice,
            selectedFuelIds = selectedFuelIds,
        ).take(maxCount.coerceAtLeast(1))
    }

    /**
     * Camera for Android Auto map: user-centered, zoom derived from 1–2 focus stations in [stations].
     */
    fun cameraForMapFocus(
        userLat: Double,
        userLon: Double,
        stations: List<Poi>,
        mapWidthPx: Int,
        mapHeightPx: Int,
        fallbackZoom: Int = DEFAULT_ZOOM,
        sortByPrice: Boolean = false,
        selectedFuelIds: Set<String> = emptySet(),
    ): Camera {
        val focus = selectMapFocusStations(
            userLat = userLat,
            userLon = userLon,
            stations = stations,
            sortByPrice = sortByPrice,
            selectedFuelIds = selectedFuelIds,
        )
        return fitToUserAndStations(
            userLat = userLat,
            userLon = userLon,
            stations = focus,
            mapWidthPx = mapWidthPx,
            mapHeightPx = mapHeightPx,
            fallbackZoom = fallbackZoom,
        )
    }

    /**
     * Fits the camera so [userLat]/[userLon] and all [stations] are visible with padding.
     * When [stations] is empty, centers on the user at [fallbackZoom].
     */
    fun fitToUserAndStations(
        userLat: Double,
        userLon: Double,
        stations: List<Poi>,
        mapWidthPx: Int,
        mapHeightPx: Int,
        fallbackZoom: Int = DEFAULT_ZOOM,
        paddingFraction: Double = 0.12,
    ): Camera {
        if (mapWidthPx <= 0 || mapHeightPx <= 0) {
            return Camera(userLat, userLon, fallbackZoom.coerceIn(MIN_ZOOM, MAX_ZOOM))
        }
        if (stations.isEmpty()) {
            return Camera(userLat, userLon, fallbackZoom.coerceIn(MIN_ZOOM, MAX_ZOOM))
        }

        var minLat = userLat
        var maxLat = userLat
        var minLng = userLon
        var maxLng = userLon
        stations.forEach { poi ->
            minLat = minOf(minLat, poi.latitude)
            maxLat = maxOf(maxLat, poi.latitude)
            minLng = minOf(minLng, poi.longitude)
            maxLng = maxOf(maxLng, poi.longitude)
        }

        val minSpanLat = 0.002
        val minSpanLng = 0.002
        if (maxLat - minLat < minSpanLat) {
            val mid = (maxLat + minLat) / 2.0
            minLat = mid - minSpanLat / 2.0
            maxLat = mid + minSpanLat / 2.0
        }
        if (maxLng - minLng < minSpanLng) {
            val mid = (maxLng + minLng) / 2.0
            minLng = mid - minSpanLng / 2.0
            maxLng = mid + minSpanLng / 2.0
        }

        val dLat = (maxLat - minLat).coerceAtLeast(minSpanLat)
        val dLng = (maxLng - minLng).coerceAtLeast(minSpanLng)
        val padLat = dLat * paddingFraction
        val padLng = dLng * paddingFraction
        minLat -= padLat
        maxLat += padLat
        minLng -= padLng
        maxLng += padLng

        // Center on user position as requested.
        // To keep all stations visible, we must use a symmetric span around the user.
        val latSpan = max(abs(maxLat - userLat), abs(minLat - userLat))
        val lngSpan = max(abs(maxLng - userLon), abs(minLng - userLon))

        val symmetricMinLat = userLat - latSpan
        val symmetricMaxLat = userLat + latSpan
        val symmetricMinLng = userLon - lngSpan
        val symmetricMaxLng = userLon + lngSpan

        val zoom = zoomForBounds(
            symmetricMinLat, symmetricMaxLat, symmetricMinLng, symmetricMaxLng,
            mapWidthPx, mapHeightPx, paddingFraction = 0.0
        ).coerceIn(MIN_ZOOM, MAX_ZOOM)

        return Camera(userLat, userLon, zoom)
    }

    internal fun zoomForBounds(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double,
        mapWidthPx: Int,
        mapHeightPx: Int,
        paddingFraction: Double = 0.12,
    ): Int {
        val pad = paddingFraction.coerceIn(0.0, 0.4)
        val w = mapWidthPx * (1.0 - 2.0 * pad).coerceAtLeast(1.0)
        val h = mapHeightPx * (1.0 - 2.0 * pad).coerceAtLeast(1.0)

        val latSpan = abs(mercatorY(maxLat) - mercatorY(minLat)).coerceAtLeast(1e-9)
        val lngSpan = abs(maxLng - minLng).let { if (it > 360) it % 360 else it } / 360.0
        val effectiveLngSpan = lngSpan.coerceAtLeast(1e-9)

        val zoomLat = log2(h / (256.0 * latSpan))
        val zoomLng = log2(w / (256.0 * effectiveLngSpan))
        return floor(min(zoomLat, zoomLng)).toInt().coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    /** Normalized Web Mercator Y in [0, 1]. */
    internal fun mercatorY(lat: Double): Double {
        val latRad = lat * PI / 180.0
        return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0
    }
}
