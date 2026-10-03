package fr.geoking.gaston.aac

import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Directional danger-zone rectangle for map overlays.
 *
 * Near short edge sits on the radar / control pin; the far short edge lies on the
 * entry boundary opposite the monitored traffic bearing (approach corridor).
 * No shape when direction is missing or bidirectional — only the POI marker should
 * be shown.
 */
object DangerZoneTriangle {
    const val RAW_DIRECTION = "direction"
    const val RAW_MONITORED_BEARING = "monitored_bearing"
    const val RAW_BIDIRECTIONAL = "direction_bidirectional"

    /** Half-width of the corridor rectangle (meters), independent of zone length. */
    const val HALF_WIDTH_METERS = 50.0

    private const val EARTH_RADIUS_M = 6_371_000.0

    /**
     * Resolves a unidirectional monitored bearing from radar POI [rawSourceData], or null
     * when direction is missing / bidirectional / not drawable.
     */
    fun monitoredBearingDegrees(poi: Poi): Double? {
        if (poi.poiCategory != PoiCategory.Radar) return null
        val raw = poi.rawSourceData ?: return null
        if (raw[RAW_BIDIRECTIONAL]?.equals("true", ignoreCase = true) == true) return null
        raw[RAW_MONITORED_BEARING]?.toDoubleOrNull()?.let {
            return OsmSpeedCameraDirection.normalizeBearing(it)
        }
        val info = OsmSpeedCameraDirection.resolve(directionTag = raw[RAW_DIRECTION])
        if (info.bidirectional) return null
        return info.monitoredBearingDegrees
    }

    /**
     * Closed ring nearLeft → farLeft → farRight → nearRight → nearLeft (5 points)
     * in (lat, lon), or null when the POI has no drawable unidirectional corridor.
     */
    fun latLngRingForRadarPoi(poi: Poi): List<Pair<Double, Double>>? {
        val bearing = monitoredBearingDegrees(poi) ?: return null
        val radiusM = DangerZoneDistances.radiusMetersForRadarPoiVma(poi.rawSourceData?.get("vma"))
        if (radiusM <= 0.0) return null
        return latLngRing(
            tipLat = poi.latitude,
            tipLon = poi.longitude,
            radiusMeters = radiusM,
            monitoredBearingDegrees = bearing,
        )
    }

    /**
     * @param monitoredBearingDegrees travel direction of controlled traffic (near edge at
     *   the radar; far edge is upstream at the entry boundary).
     */
    fun latLngRing(
        tipLat: Double,
        tipLon: Double,
        radiusMeters: Double,
        monitoredBearingDegrees: Double,
        halfWidthMeters: Double = HALF_WIDTH_METERS,
    ): List<Pair<Double, Double>> {
        val approachFrom = OsmSpeedCameraDirection.normalizeBearing(monitoredBearingDegrees + 180.0)
        val leftBearing = OsmSpeedCameraDirection.normalizeBearing(approachFrom - 90.0)
        val rightBearing = OsmSpeedCameraDirection.normalizeBearing(approachFrom + 90.0)

        val entryCenter = offsetMeters(tipLat, tipLon, approachFrom, radiusMeters)
        val nearLeft = offsetMeters(tipLat, tipLon, leftBearing, halfWidthMeters)
        val nearRight = offsetMeters(tipLat, tipLon, rightBearing, halfWidthMeters)
        val farLeft = offsetMeters(entryCenter.first, entryCenter.second, leftBearing, halfWidthMeters)
        val farRight = offsetMeters(entryCenter.first, entryCenter.second, rightBearing, halfWidthMeters)

        return listOf(
            nearLeft,
            farLeft,
            farRight,
            nearRight,
            nearLeft,
        )
    }

    /** Destination point [distanceMeters] along [bearingDegrees] from [lat]/[lon]. */
    fun offsetMeters(
        lat: Double,
        lon: Double,
        bearingDegrees: Double,
        distanceMeters: Double,
    ): Pair<Double, Double> {
        val br = toRadians(OsmSpeedCameraDirection.normalizeBearing(bearingDegrees))
        val lat1 = toRadians(lat)
        val lon1 = toRadians(lon)
        val ang = distanceMeters / EARTH_RADIUS_M
        val lat2 = asin(sin(lat1) * cos(ang) + cos(lat1) * sin(ang) * cos(br))
        val lon2 = lon1 + atan2(
            sin(br) * sin(ang) * cos(lat1),
            cos(ang) - sin(lat1) * sin(lat2),
        )
        return toDegrees(lat2) to toDegrees(lon2)
    }

    private fun toRadians(degrees: Double): Double = degrees * PI / 180.0
    private fun toDegrees(radians: Double): Double = radians * 180.0 / PI
}
