package fr.geoking.gaston.parked

/**
 * Prefer frozen [ParkCandidate] coords when saving after an AA park suggestion.
 */
object ParkCandidateSaveHelper {
    fun resolveSaveCoords(
        candidate: ParkCandidate?,
        vehicleId: String,
        fallbackLat: Double?,
        fallbackLon: Double?,
    ): Pair<Double, Double>? {
        if (candidate != null && candidate.vehicleId == vehicleId) {
            return candidate.latitude to candidate.longitude
        }
        val lat = fallbackLat ?: return null
        val lon = fallbackLon ?: return null
        return lat to lon
    }
}
