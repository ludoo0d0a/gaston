package fr.geoking.gaston.parked

/**
 * Short-lived parking spot captured when an Android Auto session ends after driving.
 * Coords are frozen at session destroy so walking with the phone cannot overwrite them.
 */
data class ParkCandidate(
    val vehicleId: String,
    val latitude: Double,
    val longitude: Double,
    val createdAtEpochMs: Long,
    val aaSuggestionShown: Boolean = false,
    val savedFromAa: Boolean = false,
)
