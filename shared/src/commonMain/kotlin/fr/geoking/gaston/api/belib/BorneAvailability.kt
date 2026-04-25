package fr.geoking.gaston.api.belib

/**
 * Status of a single charging point (EVSE) from an availability provider (e.g. Belib).
 * Aligned with Belib statuts: Disponible, Occupé, En maintenance, Réservé, Inconnu, etc.
 */
enum class AvailabilityStatus {
    Available,
    Occupied,
    Maintenance,
    Reserved,
    Unknown,
    NotImplemented,
    ComingIntoService,
    PlannedIntoService,
    Removed
}

/**
 * Availability data for one charging point (EVSE).
 */
data class PdcAvailability(
    val id: String,
    val status: AvailabilityStatus,
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val stationId: String? = null
)

/**
 * Summary of availability for a station (multiple PDCs), for UI display.
 */
data class StationAvailabilitySummary(
    val availableCount: Int,
    val totalCount: Int
)

/**
 * Provider of real-time availability for charging points in a given area.
 * Returns per-EVSE status; callers can aggregate by station and match to [fr.geoking.gaston.poi.Poi].
 */
interface BorneAvailabilityProvider {
    suspend fun getAvailability(
        latitude: Double,
        longitude: Double,
        radiusKm: Int
    ): List<PdcAvailability>
}
