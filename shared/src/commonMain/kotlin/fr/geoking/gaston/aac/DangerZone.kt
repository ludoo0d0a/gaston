package fr.geoking.gaston.aac

import fr.geoking.gaston.shared.location.haversineKm
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Road network class used to pick AAC alert distances (AFFTAC-style heuristics).
 * Prefer OSM map-matching via [OsmRoadClassifier]; VMA / thoroughfare are fallbacks.
 */
enum class RoadNetworkClass {
    /** ~4 km alert radius */
    Motorway,
    /** ~2 km alert radius */
    ExtraUrban,
    /** ~300 m alert radius */
    Urban,
}

/**
 * Internal zone kinds — not user-facing labels.
 * UX must speak of "zone de danger" / VMA, never "contrôle ici".
 */
enum class DangerZoneKind {
    SpeedControlArea,
    AccidentProne,
    HeightenedVigilance,
    RoadSafetyMessage,
}

/**
 * Extended danger zone (circle). Alert on entry / presence — never on distance-to-control-pin alone.
 *
 * [centerLatitude]/[centerLongitude] are the geometric center of the zone buffer, not an
 * exposed "exact control point" for alert UX. Callers must not present these as a radar pin.
 */
data class DangerZone(
    val id: String,
    val centerLatitude: Double,
    val centerLongitude: Double,
    val radiusMeters: Double,
    val speedLimitKmH: Int?,
    val kind: DangerZoneKind,
    val source: String,
    val roadClass: RoadNetworkClass,
) {
    fun contains(latitude: Double, longitude: Double): Boolean {
        val distM = haversineKm(latitude, longitude, centerLatitude, centerLongitude) * 1000.0
        return distM <= radiusMeters
    }

    fun distanceMetersFrom(latitude: Double, longitude: Double): Double {
        return haversineKm(latitude, longitude, centerLatitude, centerLongitude) * 1000.0
    }
}

object DangerZoneDistances {
    const val MOTORWAY_METERS = 4_000.0
    const val EXTRA_URBAN_METERS = 2_000.0
    const val URBAN_METERS = 300.0

    fun radiusMetersFor(roadClass: RoadNetworkClass): Double = when (roadClass) {
        RoadNetworkClass.Motorway -> MOTORWAY_METERS
        RoadNetworkClass.ExtraUrban -> EXTRA_URBAN_METERS
        RoadNetworkClass.Urban -> URBAN_METERS
    }

    /**
     * Heuristic road class from posted speed limit (VMA) when map-matching is unavailable.
     * ≥110 → motorway, ≥70 → extra-urban, else urban.
     */
    fun classifyFromSpeedLimitKmH(speedLimitKmH: Int?): RoadNetworkClass = when {
        speedLimitKmH != null && speedLimitKmH >= 110 -> RoadNetworkClass.Motorway
        speedLimitKmH != null && speedLimitKmH >= 70 -> RoadNetworkClass.ExtraUrban
        else -> RoadNetworkClass.Urban
    }

    /**
     * Road class from an OSM `highway=*` tag. Returns null for blank/unknown tags.
     * motorway / motorway_link → Motorway; trunk / primary (+ links) → ExtraUrban; else Urban.
     */
    fun fromOsmHighway(tag: String?): RoadNetworkClass? {
        val t = tag?.trim()?.lowercase().orEmpty()
        if (t.isEmpty()) return null
        return when (t) {
            "motorway", "motorway_link" -> RoadNetworkClass.Motorway
            "trunk", "trunk_link", "primary", "primary_link" -> RoadNetworkClass.ExtraUrban
            else -> RoadNetworkClass.Urban
        }
    }

    /** Rank for picking the strongest highway among several nearby ways (higher = stronger). */
    fun osmHighwayRank(tag: String?): Int = when (fromOsmHighway(tag)) {
        RoadNetworkClass.Motorway -> 3
        RoadNetworkClass.ExtraUrban -> 2
        RoadNetworkClass.Urban -> 1
        null -> 0
    }
}

/**
 * Converts a fixed speed-control dataset point into an extended [DangerZone].
 * The resulting geometry is a buffer around the source coordinates; alert UX must not
 * treat the center as a precise control location marker.
 */
object DangerZoneFactory {
    fun fromSpeedControlPoint(
        id: String,
        latitude: Double,
        longitude: Double,
        speedLimitKmH: Int?,
        source: String,
        csvType: String? = null,
        roadClassOverride: RoadNetworkClass? = null,
    ): DangerZone {
        val roadClass = roadClassOverride
            ?: DangerZoneDistances.classifyFromSpeedLimitKmH(speedLimitKmH)
        val kind = mapCsvTypeToKind(csvType)
        return DangerZone(
            id = id,
            centerLatitude = latitude,
            centerLongitude = longitude,
            radiusMeters = DangerZoneDistances.radiusMetersFor(roadClass),
            speedLimitKmH = speedLimitKmH?.takeIf { it > 0 },
            kind = kind,
            source = source,
            roadClass = roadClass,
        )
    }

    /**
     * Maps open-data CSV radar types to zone kinds without implying "control here" in UX.
     * Non-speed types become heightened vigilance areas.
     */
    fun mapCsvTypeToKind(csvType: String?): DangerZoneKind {
        val t = csvType?.uppercase()?.trim().orEmpty()
        return when {
            t.contains("FEU") || t.contains("FEUX") || t.contains("ETFR") ->
                DangerZoneKind.HeightenedVigilance
            t.contains("ETPN") || t.contains("PASSAGE") ->
                DangerZoneKind.HeightenedVigilance
            t.contains("CHANTIER") || t.contains("TRAVAUX") ->
                DangerZoneKind.HeightenedVigilance
            t.isEmpty() ||
                t.contains("FIXE") ||
                t.contains("ETD") ||
                t.contains("ETF") ||
                t.contains("ETT") ||
                t.contains("ETU") ||
                t.contains("ETVM") ||
                t.contains("DISCRIMINANT") ||
                t.contains("TRONCON") ||
                t.contains("TRONÇON") ||
                t.contains("MOBILE") ||
                t.contains("VITESSE") -> DangerZoneKind.SpeedControlArea
            else -> DangerZoneKind.SpeedControlArea
        }
    }

    fun accidentProne(
        id: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
        speedLimitKmH: Int? = null,
        source: String,
        roadClass: RoadNetworkClass = DangerZoneDistances.classifyFromSpeedLimitKmH(speedLimitKmH),
    ): DangerZone = DangerZone(
        id = id,
        centerLatitude = latitude,
        centerLongitude = longitude,
        radiusMeters = radiusMeters,
        speedLimitKmH = speedLimitKmH,
        kind = DangerZoneKind.AccidentProne,
        source = source,
        roadClass = roadClass,
    )
}

/**
 * Pure evaluation: is the vehicle inside a zone (optionally ahead on trajectory)?
 */
data class DangerZoneEvaluation(
    val zoneId: String,
    val isInside: Boolean,
    val distanceToCenterMeters: Double,
    val isAhead: Boolean,
    val speedLimitKmH: Int?,
    val currentSpeedKmH: Double,
    val isOverspeed: Boolean,
    val kind: DangerZoneKind,
)

object DangerZoneEvaluator {
    const val DEFAULT_TOLERANCE_ANGLE_DEGREES = 40.0
    const val MIN_SPEED_KMH_FOR_BEARING = 5.0

    private fun toRadians(degrees: Double): Double = degrees * PI / 180.0
    private fun toDegrees(radians: Double): Double = radians * 180.0 / PI

    fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = toRadians(lat1)
        val lat2Rad = toRadians(lat2)
        val dLonRad = toRadians(lon2 - lon1)
        val y = sin(dLonRad) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLonRad)
        var bearing = toDegrees(atan2(y, x))
        bearing = (bearing + 360.0) % 360.0
        return bearing
    }

    fun angleDifference(bearing1: Double, bearing2: Double): Double {
        return abs((bearing1 - bearing2 + 540.0) % 360.0 - 180.0)
    }

    fun evaluate(
        vehLat: Double,
        vehLon: Double,
        vehSpeedKmH: Double,
        vehBearing: Double?,
        zone: DangerZone,
        toleranceAngleDegrees: Double = DEFAULT_TOLERANCE_ANGLE_DEGREES,
    ): DangerZoneEvaluation {
        val distanceMeters = zone.distanceMetersFrom(vehLat, vehLon)
        val isInside = distanceMeters <= zone.radiusMeters

        val bearingToCenter = calculateBearing(vehLat, vehLon, zone.centerLatitude, zone.centerLongitude)
        val isAhead = if (vehBearing != null && vehSpeedKmH >= MIN_SPEED_KMH_FOR_BEARING) {
            angleDifference(vehBearing, bearingToCenter) <= toleranceAngleDegrees
        } else {
            true
        }

        // Alert on presence in zone; when moving, prefer zone ahead of trajectory
        // (reduces opposite-carriageway noise without requiring full map-matching).
        val alertActive = if (vehBearing != null && vehSpeedKmH >= MIN_SPEED_KMH_FOR_BEARING) {
            isInside && isAhead
        } else {
            isInside
        }

        val speedLimit = zone.speedLimitKmH
        val isOverspeed = speedLimit != null && vehSpeedKmH > speedLimit

        return DangerZoneEvaluation(
            zoneId = zone.id,
            isInside = alertActive,
            distanceToCenterMeters = distanceMeters,
            isAhead = isAhead,
            speedLimitKmH = speedLimit,
            currentSpeedKmH = vehSpeedKmH,
            isOverspeed = isOverspeed,
            kind = zone.kind,
        )
    }
}
