package fr.geoking.gaston.api.dotnl

import fr.geoking.gaston.api.belib.BorneAvailabilityProvider
import fr.geoking.gaston.api.belib.PdcAvailability
import fr.geoking.gaston.api.common.OcpiEvseAvailability

/**
 * [BorneAvailabilityProvider] for Netherlands DOT-NL EV availability
 * (NDW open OCPI locations dump with per-EVSE status).
 */
class DotNlAvailabilityProvider(
    private val client: DotNlAvailabilityClient,
    private val radiusKm: Int = 15,
    private val limit: Int = 200,
) : BorneAvailabilityProvider {

    override suspend fun getAvailability(
        latitude: Double,
        longitude: Double,
        radiusKm: Int,
    ): List<PdcAvailability> {
        val effectiveRadius = if (radiusKm > 0) radiusKm else this.radiusKm
        val records = client.getAvailability(
            latitude = latitude,
            longitude = longitude,
            radiusKm = effectiveRadius,
            limit = limit,
        )
        return records.map { record ->
            PdcAvailability(
                id = record.id,
                status = OcpiEvseAvailability.mapStatus(record.statusRaw),
                latitude = record.latitude,
                longitude = record.longitude,
                address = record.address,
                stationId = record.stationId,
            )
        }
    }
}
