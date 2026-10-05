package fr.geoking.gaston.parked

import fr.geoking.gaston.shared.location.haversineKm

/**
 * Phase B: trigger when the phone has moved [thresholdMeters] away from the frozen park candidate.
 */
class WalkAwayDistanceTracker(
    private val originLat: Double,
    private val originLon: Double,
    private val thresholdMeters: Double = THRESHOLD_METERS,
) {
    enum class Result {
        Continue,
        Triggered,
    }

    fun onSample(latitude: Double, longitude: Double): Result {
        val meters = haversineKm(originLat, originLon, latitude, longitude) * 1000.0
        return if (meters >= thresholdMeters) Result.Triggered else Result.Continue
    }

    companion object {
        const val THRESHOLD_METERS: Double = 50.0
    }
}
