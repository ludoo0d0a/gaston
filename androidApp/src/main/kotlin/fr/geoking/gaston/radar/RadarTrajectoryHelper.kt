package fr.geoking.gaston.radar

import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.shared.location.haversineKm
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Result of evaluating a radar against vehicle movement parameters.
 */
data class RadarEvaluationResult(
    val radarId: String,
    val distanceMeters: Double,
    val isAhead: Boolean,
    val isWithinWarningDistance: Boolean,
    val speedLimitKmH: Int?,
    val currentSpeedKmH: Double,
    val isOverspeed: Boolean
)

object RadarTrajectoryHelper {

    const val DEFAULT_TOLERANCE_ANGLE_DEGREES = 40.0
    const val MIN_SPEED_KMH_FOR_BEARING = 5.0

    /**
     * Calculates the initial bearing (heading) in degrees [0, 360) from (lat1, lon1) to (lat2, lon2).
     */
    fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val dLonRad = Math.toRadians(lon2 - lon1)

        val y = sin(dLonRad) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLonRad)

        var bearing = Math.toDegrees(atan2(y, x))
        bearing = (bearing + 360.0) % 360.0
        return bearing
    }

    /**
     * Calculates the smallest angular difference in degrees [0, 180] between two bearings.
     */
    fun angleDifference(bearing1: Double, bearing2: Double): Double {
        val diff = abs((bearing1 - bearing2 + 540.0) % 360.0 - 180.0)
        return diff
    }

    /**
     * Extracts speed limit in km/h from POI rawSourceData or name.
     */
    fun extractSpeedLimitKmH(poi: Poi): Int? {
        val rawVma = poi.rawSourceData?.get("vma")
        if (rawVma != null && rawVma != "NA") {
            rawVma.toIntOrNull()?.let { if (it > 0) return it }
        }

        // Fallback: parse from name e.g. "Radar 130 km/h" or "130"
        val name = poi.name
        val regex = Regex("""\b(\d{2,3})\s*(km/h)?\b""", RegexOption.IGNORE_CASE)
        val match = regex.find(name)
        if (match != null) {
            val speed = match.groupValues[1].toIntOrNull()
            if (speed != null && speed in 10..150) {
                return speed
            }
        }
        return null
    }

    /**
     * Evaluates whether a radar POI is ahead of the vehicle in its trajectory and within warning range.
     */
    fun evaluateRadar(
        vehLat: Double,
        vehLon: Double,
        vehSpeedKmH: Double,
        vehBearing: Double?,
        radar: Poi,
        warningDistanceMeters: Int,
        toleranceAngleDegrees: Double = DEFAULT_TOLERANCE_ANGLE_DEGREES
    ): RadarEvaluationResult {
        val distanceKm = haversineKm(vehLat, vehLon, radar.latitude, radar.longitude)
        val distanceMeters = distanceKm * 1000.0

        val bearingToRadar = calculateBearing(vehLat, vehLon, radar.latitude, radar.longitude)

        // Check if radar is ahead based on bearing
        val isAhead = if (vehBearing != null && vehSpeedKmH >= MIN_SPEED_KMH_FOR_BEARING) {
            val angleDiff = angleDifference(vehBearing, bearingToRadar)
            angleDiff <= toleranceAngleDegrees
        } else {
            // Low speed or missing bearing: assume ahead if within range
            true
        }

        val isWithinWarningDistance = isAhead && distanceMeters <= warningDistanceMeters
        val speedLimit = extractSpeedLimitKmH(radar)
        val isOverspeed = if (speedLimit != null) vehSpeedKmH > speedLimit else false

        return RadarEvaluationResult(
            radarId = radar.id,
            distanceMeters = distanceMeters,
            isAhead = isAhead,
            isWithinWarningDistance = isWithinWarningDistance,
            speedLimitKmH = speedLimit,
            currentSpeedKmH = vehSpeedKmH,
            isOverspeed = isOverspeed
        )
    }
}
