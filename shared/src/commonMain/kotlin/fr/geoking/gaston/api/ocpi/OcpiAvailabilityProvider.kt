package fr.geoking.julius.api.ocpi

import fr.geoking.julius.api.belib.AvailabilityStatus
import fr.geoking.julius.api.belib.BorneAvailabilityProvider
import fr.geoking.julius.api.belib.PdcAvailability
import fr.geoking.julius.shared.location.haversineKm

/**
 * [BorneAvailabilityProvider] implementation for OCPI-compliant CPOs.
 */
class OcpiAvailabilityProvider(
    private val client: OcpiClient
) : BorneAvailabilityProvider {

    override suspend fun getAvailability(
        latitude: Double,
        longitude: Double,
        radiusKm: Int
    ): List<PdcAvailability> {
        val locations = client.getLocations(latitude, longitude, radiusKm)

        return locations.flatMap { loc ->
            val locLat = loc.coordinates.latitude.toDoubleOrNull() ?: 0.0
            val locLon = loc.coordinates.longitude.toDoubleOrNull() ?: 0.0

            // Filter by distance if the API didn't do it
            val distance = haversineKm(latitude, longitude, locLat, locLon)
            if (distance > radiusKm) return@flatMap emptyList()

            loc.evses.map { evse ->
                PdcAvailability(
                    id = evse.uid,
                    status = mapStatus(evse.status),
                    latitude = locLat,
                    longitude = locLon,
                    address = loc.address,
                    stationId = loc.id
                )
            }
        }
    }

    private fun mapStatus(status: OcpiStatus): AvailabilityStatus {
        return when (status) {
            OcpiStatus.AVAILABLE -> AvailabilityStatus.Available
            OcpiStatus.CHARGING -> AvailabilityStatus.Occupied
            OcpiStatus.BLOCKED -> AvailabilityStatus.Occupied
            OcpiStatus.RESERVED -> AvailabilityStatus.Reserved
            OcpiStatus.INOPERATIVE -> AvailabilityStatus.Maintenance
            OcpiStatus.OUTOFORDER -> AvailabilityStatus.Maintenance
            OcpiStatus.PLANNED -> AvailabilityStatus.PlannedIntoService
            OcpiStatus.REMOVED -> AvailabilityStatus.Removed
            OcpiStatus.UNKNOWN -> AvailabilityStatus.Unknown
        }
    }
}
