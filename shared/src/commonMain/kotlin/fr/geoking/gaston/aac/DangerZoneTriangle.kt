package fr.geoking.gaston.aac

import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.shared.location.haversineKm
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Kind of alert-distance overlay ring (canonical colours live here for all map surfaces).
 */
enum class DangerZoneOverlayKind {
    /** Full AFFTAC sector / circle. */
    Full,
    /** Intermediate kilometre reminder band. */
    KmReminder,
    /** Final near-center alert (200 m). */
    Near,
}

/**
 * Precomputed lat/lon ring + colours for one alert distance.
 * Renderers only project [points]; no local geometry.
 */
data class DangerZoneOverlayRing(
    val radiusMeters: Double,
    val kind: DangerZoneOverlayKind,
    /** Fill ARGB (including alpha). */
    val fillArgb: Int,
    /** Stroke ARGB. */
    val strokeArgb: Int,
    /** Closed ring (first == last) in (lat, lon). */
    val points: List<Pair<Double, Double>>,
)

/**
 * Directional danger-zone geometry: circular **sector** (part of a disk) for map overlays
 * and alert hit-testing — same shape for detection and all map types.
 *
 * - Unidirectional monitored bearing → sector of radius R, half-angle [HALF_ANGLE_DEGREES],
 *   axis = approach from (`monitoredBearing + 180°`).
 * - Missing / bidirectional direction → full AFFTAC circle of R.
 *
 * Overlay rings for each alert radius (R, km milestones, 200 m) share this geometry.
 */
object DangerZoneTriangle {
    const val RAW_DIRECTION = "direction"
    const val RAW_MONITORED_BEARING = "monitored_bearing"
    const val RAW_BIDIRECTIONAL = "direction_bidirectional"

    /** Half-aperture of the approach sector (degrees). Total opening = 90°. */
    const val HALF_ANGLE_DEGREES = 45.0

    /** Final near-center alert distance (meters). */
    const val NEAR_ALERT_METERS = 200.0

    /** Kilometre reminder step (meters). */
    const val KM_REMINDER_METERS = 1_000.0

    /** @deprecated Trapezoid widths removed; kept for binary/compat references in tests. */
    @Deprecated("Sector geometry — use HALF_ANGLE_DEGREES")
    const val HALF_WIDTH_METERS = 50.0

    /** @deprecated Trapezoid widths removed; kept for binary/compat references in tests. */
    @Deprecated("Sector geometry — use HALF_ANGLE_DEGREES")
    const val FAR_HALF_WIDTH_METERS = 150.0

    private const val EARTH_RADIUS_M = 6_371_000.0
    private const val ARC_SEGMENTS_SECTOR = 32
    private const val ARC_SEGMENTS_CIRCLE = 48

    // Canonical ARGB colours (shared by phone + AA maps).
    private const val FILL_FULL = 0x22EF4444
    private const val STROKE_FULL = 0xFFEF4444.toInt()
    private const val FILL_NEAR = 0x55F97316
    private const val STROKE_NEAR = 0xFFF97316.toInt()
    private val FILL_KM = intArrayOf(0x28F59E0B, 0x28EAB308, 0x28FBBF24, 0x28FCD34D)
    private val STROKE_KM = intArrayOf(
        0xFFF59E0B.toInt(),
        0xFFEAB308.toInt(),
        0xFFFBBF24.toInt(),
        0xFFFCD34D.toInt(),
    )

    /**
     * Resolves a unidirectional monitored bearing from radar POI [rawSourceData], or null
     * when direction is missing / bidirectional (full-circle geometry).
     */
    fun monitoredBearingDegrees(poi: Poi): Double? {
        if (poi.poiCategory != PoiCategory.Radar) return null
        val raw = poi.rawSourceData ?: return null
        if (
            raw[RAW_BIDIRECTIONAL]?.equals("true", ignoreCase = true) == true ||
            raw["bidirectional"]?.equals("true", ignoreCase = true) == true
        ) {
            return null
        }
        raw[RAW_MONITORED_BEARING]?.toDoubleOrNull()?.let {
            return OsmSpeedCameraDirection.normalizeBearing(it)
        }
        val info = OsmSpeedCameraDirection.resolve(directionTag = raw[RAW_DIRECTION])
        if (info.bidirectional) return null
        return info.monitoredBearingDegrees
    }

    /**
     * Alert radii to draw / fire, largest first: full R, then km milestones &lt; R and &gt; 200 m,
     * then 200 m when R &gt; 200.
     */
    fun alertRadiiMeters(zoneRadiusMeters: Double): List<Double> {
        if (zoneRadiusMeters <= 0.0) return emptyList()
        val out = mutableListOf(zoneRadiusMeters)
        var n = floor((zoneRadiusMeters - 1.0) / KM_REMINDER_METERS).toInt()
        while (n >= 1) {
            val km = n * KM_REMINDER_METERS
            if (km < zoneRadiusMeters && km > NEAR_ALERT_METERS) {
                out.add(km)
            }
            n--
        }
        if (NEAR_ALERT_METERS < zoneRadiusMeters) {
            out.add(NEAR_ALERT_METERS)
        }
        return out
    }

    /** Km reminder thresholds only (no full R, no 200 m). */
    fun kmReminderRadiiMeters(zoneRadiusMeters: Double): List<Double> =
        alertRadiiMeters(zoneRadiusMeters).filter {
            it != zoneRadiusMeters && it != NEAR_ALERT_METERS
        }

    fun overlayKindForRadius(radiusMeters: Double, zoneRadiusMeters: Double): DangerZoneOverlayKind =
        when {
            radiusMeters == NEAR_ALERT_METERS ||
                (radiusMeters <= NEAR_ALERT_METERS + 0.5 && radiusMeters >= NEAR_ALERT_METERS - 0.5) ->
                DangerZoneOverlayKind.Near
            radiusMeters >= zoneRadiusMeters - 0.5 -> DangerZoneOverlayKind.Full
            else -> DangerZoneOverlayKind.KmReminder
        }

    /**
     * All colored overlay rings for a radar POI (largest → smallest). Empty for non-radar.
     */
    fun overlayRingsForRadarPoi(poi: Poi): List<DangerZoneOverlayRing> {
        if (poi.poiCategory != PoiCategory.Radar) return emptyList()
        val zoneRadius = DangerZoneDistances.radiusMetersForRadarPoiVma(poi.rawSourceData?.get("vma"))
        if (zoneRadius <= 0.0) return emptyList()
        val bearing = monitoredBearingDegrees(poi)
        return overlayRings(
            tipLat = poi.latitude,
            tipLon = poi.longitude,
            zoneRadiusMeters = zoneRadius,
            monitoredBearingDegrees = bearing,
        )
    }

    fun overlayRingsForZone(zone: DangerZone): List<DangerZoneOverlayRing> =
        overlayRings(
            tipLat = zone.centerLatitude,
            tipLon = zone.centerLongitude,
            zoneRadiusMeters = zone.radiusMeters,
            monitoredBearingDegrees = if (zone.usesApproachTrapezoid) zone.monitoredBearingDegrees else null,
        )

    fun overlayRings(
        tipLat: Double,
        tipLon: Double,
        zoneRadiusMeters: Double,
        monitoredBearingDegrees: Double?,
    ): List<DangerZoneOverlayRing> {
        val radii = alertRadiiMeters(zoneRadiusMeters)
        var kmIndex = 0
        return radii.map { r ->
            val kind = overlayKindForRadius(r, zoneRadiusMeters)
            val (fill, stroke) = colorsFor(kind, kmIndex)
            if (kind == DangerZoneOverlayKind.KmReminder) kmIndex++
            val points = latLngRing(
                tipLat = tipLat,
                tipLon = tipLon,
                radiusMeters = r,
                monitoredBearingDegrees = monitoredBearingDegrees,
            )
            DangerZoneOverlayRing(
                radiusMeters = r,
                kind = kind,
                fillArgb = fill,
                strokeArgb = stroke,
                points = points,
            )
        }
    }

    private fun colorsFor(kind: DangerZoneOverlayKind, kmIndex: Int): Pair<Int, Int> = when (kind) {
        DangerZoneOverlayKind.Full -> FILL_FULL to STROKE_FULL
        DangerZoneOverlayKind.Near -> FILL_NEAR to STROKE_NEAR
        DangerZoneOverlayKind.KmReminder -> {
            val i = kmIndex.coerceIn(0, FILL_KM.lastIndex)
            FILL_KM[i] to STROKE_KM[i]
        }
    }

    /**
     * Closed ring for one radius: sector when [monitoredBearingDegrees] is set, else full circle.
     * Always returns a ring (never null) when [radiusMeters] &gt; 0.
     */
    fun latLngRingForRadarPoi(poi: Poi): List<Pair<Double, Double>>? {
        if (poi.poiCategory != PoiCategory.Radar) return null
        val radiusM = DangerZoneDistances.radiusMetersForRadarPoiVma(poi.rawSourceData?.get("vma"))
        if (radiusM <= 0.0) return null
        return latLngRing(
            tipLat = poi.latitude,
            tipLon = poi.longitude,
            radiusMeters = radiusM,
            monitoredBearingDegrees = monitoredBearingDegrees(poi),
        )
    }

    /**
     * @param monitoredBearingDegrees travel direction of controlled traffic; sector opens on
     *   the approach side (`bearing + 180°`). Null → full circle.
     */
    fun latLngRing(
        tipLat: Double,
        tipLon: Double,
        radiusMeters: Double,
        monitoredBearingDegrees: Double?,
        halfAngleDegrees: Double = HALF_ANGLE_DEGREES,
    ): List<Pair<Double, Double>> {
        if (radiusMeters <= 0.0) return emptyList()
        return if (monitoredBearingDegrees != null) {
            sectorRing(tipLat, tipLon, radiusMeters, monitoredBearingDegrees, halfAngleDegrees)
        } else {
            circleRing(tipLat, tipLon, radiusMeters)
        }
    }

    private fun sectorRing(
        tipLat: Double,
        tipLon: Double,
        radiusMeters: Double,
        monitoredBearingDegrees: Double,
        halfAngleDegrees: Double,
    ): List<Pair<Double, Double>> {
        val approachFrom = OsmSpeedCameraDirection.normalizeBearing(monitoredBearingDegrees + 180.0)
        val start = OsmSpeedCameraDirection.normalizeBearing(approachFrom - halfAngleDegrees)
        val sweep = halfAngleDegrees * 2.0
        val points = mutableListOf<Pair<Double, Double>>()
        points.add(tipLat to tipLon)
        for (i in 0..ARC_SEGMENTS_SECTOR) {
            val bearing = OsmSpeedCameraDirection.normalizeBearing(start + sweep * i / ARC_SEGMENTS_SECTOR)
            points.add(offsetMeters(tipLat, tipLon, bearing, radiusMeters))
        }
        points.add(tipLat to tipLon)
        return points
    }

    private fun circleRing(
        tipLat: Double,
        tipLon: Double,
        radiusMeters: Double,
    ): List<Pair<Double, Double>> {
        val points = mutableListOf<Pair<Double, Double>>()
        for (i in 0..ARC_SEGMENTS_CIRCLE) {
            val bearing = 360.0 * i / ARC_SEGMENTS_CIRCLE
            points.add(offsetMeters(tipLat, tipLon, bearing, radiusMeters))
        }
        return points
    }

    /**
     * True when [lat]/[lon] lies inside the sector (or full circle when bearing is used
     * with [contains] overload that always has a bearing — see other overload).
     */
    fun contains(
        lat: Double,
        lon: Double,
        tipLat: Double,
        tipLon: Double,
        radiusMeters: Double,
        monitoredBearingDegrees: Double,
        halfAngleDegrees: Double = HALF_ANGLE_DEGREES,
    ): Boolean = containsSectorOrCircle(
        lat = lat,
        lon = lon,
        tipLat = tipLat,
        tipLon = tipLon,
        radiusMeters = radiusMeters,
        monitoredBearingDegrees = monitoredBearingDegrees,
        halfAngleDegrees = halfAngleDegrees,
    )

    fun containsSectorOrCircle(
        lat: Double,
        lon: Double,
        tipLat: Double,
        tipLon: Double,
        radiusMeters: Double,
        monitoredBearingDegrees: Double?,
        halfAngleDegrees: Double = HALF_ANGLE_DEGREES,
    ): Boolean {
        if (radiusMeters <= 0.0) return false
        val distM = haversineKm(lat, lon, tipLat, tipLon) * 1000.0
        if (distM > radiusMeters) return false
        if (monitoredBearingDegrees == null) return true
        if (distM < 1.0) return true // at tip
        val approachFrom = OsmSpeedCameraDirection.normalizeBearing(monitoredBearingDegrees + 180.0)
        val bearingFromTip = DangerZoneEvaluator.calculateBearing(tipLat, tipLon, lat, lon)
        return DangerZoneEvaluator.angleDifference(bearingFromTip, approachFrom) <= halfAngleDegrees
    }

    /**
     * Ray-casting point-in-polygon on a closed (lat, lon) ring.
     * Uses lon as x and lat as y (adequate for local corridor scales).
     */
    fun pointInRing(lat: Double, lon: Double, ring: List<Pair<Double, Double>>): Boolean {
        if (ring.size < 4) return false
        val n = if (
            ring.size >= 2 &&
            ring.first().first == ring.last().first &&
            ring.first().second == ring.last().second
        ) {
            ring.size - 1
        } else {
            ring.size
        }
        if (n < 3) return false

        var inside = false
        var j = n - 1
        for (i in 0 until n) {
            val yi = ring[i].first
            val xi = ring[i].second
            val yj = ring[j].first
            val xj = ring[j].second
            val intersect = ((yi > lat) != (yj > lat)) &&
                (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
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
