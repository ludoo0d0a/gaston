package fr.geoking.gaston.parked

/**
 * Short-lived parking spot frozen when a stop is detected (in-session or post-AA).
 * Coords stay fixed so walking with the phone cannot overwrite them.
 */
data class ParkCandidate(
    val vehicleId: String,
    val latitude: Double,
    val longitude: Double,
    val createdAtEpochMs: Long,
    val aaSuggestionShown: Boolean = false,
    val savedFromAa: Boolean = false,
)
