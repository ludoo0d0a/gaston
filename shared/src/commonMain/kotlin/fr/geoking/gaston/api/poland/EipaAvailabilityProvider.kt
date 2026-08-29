package fr.geoking.gaston.api.poland

import fr.geoking.gaston.api.belib.BorneAvailabilityProvider
import fr.geoking.gaston.api.belib.PdcAvailability

/**
 * [BorneAvailabilityProvider] for Poland EIPA (UDT) EV availability
 * (`dynamic.json` status joined to `point` / `station` dumps).
 */
class EipaAvailabilityProvider(
    private val client: EipaAvailabilityClient,
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
                status = client.mapStatus(record.availability, record.freeStatus),
                latitude = record.latitude,
                longitude = record.longitude,
                address = record.address,
                stationId = record.stationId,
            )
        }
    }
}
