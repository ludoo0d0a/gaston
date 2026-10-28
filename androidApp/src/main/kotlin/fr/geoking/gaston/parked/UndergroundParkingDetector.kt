package fr.geoking.gaston.parked

import fr.geoking.gaston.parking.ParkingPoi
import fr.geoking.gaston.shared.location.haversineKm

/**
 * Evaluates whether a parking candidate or position is likely in an underground parking garage.
 *
 * Checks:
 * 1. Proximity (<= [MAX_UNDERGROUND_DISTANCE_METERS]) to an OSM / parking POI with underground indicators
 *    (e.g., name/description/address/provider containing "underground", "souterrain", "parking souterrain", etc.).
 * 2. Signal loss / age threshold near any parking POI (if location updates stopped or accuracy dropped significantly
 *    while near a parking POI).
 */
object UndergroundParkingDetector {

    const val MAX_UNDERGROUND_DISTANCE_METERS = 250.0

    /**
     * Keywords indicating an underground parking space in names, descriptions or tags.
     */
    private val UNDERGROUND_KEYWORDS = listOf(
        "underground",
        "souterrain",
        "parking souterrain",
        "parkhaus underground",
        "subterranean",
        "subterraneo",
        "parcheggio sotterraneo",
        "tiefgarage",
        "parking couvert",
        "indoor parking"
    )

    /**
     * Evaluates if a given coordinate (or POI list + location signal state) indicates underground parking.
     */
    fun isLikelyUnderground(
        latitude: Double,
        longitude: Double,
        nearbyParkings: List<ParkingPoi>,
        isGpsLostNearParking: Boolean = false,
    ): Boolean {
        if (nearbyParkings.isEmpty()) return false

        // Check 1: Proximity to an explicitly underground POI
        val closeUndergroundPoi = nearbyParkings.firstOrNull { poi ->
            val distMeters = haversineKm(latitude, longitude, poi.latitude, poi.longitude) * 1000.0
            distMeters <= MAX_UNDERGROUND_DISTANCE_METERS && isPoiUnderground(poi)
        }
        if (closeUndergroundPoi != null) return true

        // Check 2: Signal loss near any parking POI
        if (isGpsLostNearParking) {
            val closeParking = nearbyParkings.firstOrNull { poi ->
                val distMeters = haversineKm(latitude, longitude, poi.latitude, poi.longitude) * 1000.0
                distMeters <= MAX_UNDERGROUND_DISTANCE_METERS
            }
            if (closeParking != null) return true
        }

        return false
    }

    /**
     * Checks if a ParkingPoi contains underground keywords in its fields.
     */
    fun isPoiUnderground(poi: ParkingPoi): Boolean {
        val textToSearch = listOfNotNull(
            poi.name,
            poi.address,
            poi.state,
            poi.priceInfo
        ).joinToString(" ").lowercase()

        return UNDERGROUND_KEYWORDS.any { keyword -> textToSearch.contains(keyword) }
    }
}
