package fr.geoking.gaston.aac

import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Directional danger-zone triangle for map overlays.
 *
 * Tip sits on the radar / control pin; the base lies on the entry arc opposite the
 * monitored traffic bearing (approach corridor). No triangle when direction is missing
 * or bidirectional — only the POI marker should be shown.
 */
object DangerZoneTriangle {
    const val RAW_DIRECTION = "direction"
    const val RAW_MONITORED_BEARING = "monitored_bearing"
    const val RAW_BIDIRECTIONAL = "direction_bidirectional"

    /** Half-aperture of the isosceles wedge (degrees) from tip to each base corner. */
    const val HALF_APERTURE_DEGREES = 30.0

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
     * Closed ring tip → baseLeft → baseRight → tip (4 points) in (lat, lon), or null when
     * the POI has no drawable unidirectional corridor.
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
     * @param monitoredBearingDegrees travel direction of controlled traffic (tip points
     *   "downstream"; base is upstream at the entry boundary).
     */
    fun latLngRing(
        tipLat: Double,
        tipLon: Double,
        radiusMeters: Double,
        monitoredBearingDegrees: Double,
        halfApertureDegrees: Double = HALF_APERTURE_DEGREES,
    ): List<Pair<Double, Double>> {
        val approachFrom = OsmSpeedCameraDirection.normalizeBearing(monitoredBearingDegrees + 180.0)
        val baseLeft = offsetMeters(tipLat, tipLon, approachFrom - halfApertureDegrees, radiusMeters)
        val baseRight = offsetMeters(tipLat, tipLon, approachFrom + halfApertureDegrees, radiusMeters)
        return listOf(
            tipLat to tipLon,
            baseLeft,
            baseRight,
            tipLat to tipLon,
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
