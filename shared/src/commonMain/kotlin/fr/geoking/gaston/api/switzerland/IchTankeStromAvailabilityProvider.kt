package fr.geoking.gaston.api.switzerland

import fr.geoking.gaston.api.belib.BorneAvailabilityProvider
import fr.geoking.gaston.api.belib.PdcAvailability

/**
 * [BorneAvailabilityProvider] for Switzerland ich-tanke-strom EV availability
 * (BFE open static + status JSON dumps).
 */
class IchTankeStromAvailabilityProvider(
    private val client: IchTankeStromAvailabilityClient,
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
                status = client.mapStatus(record.statusRaw),
                latitude = record.latitude,
                longitude = record.longitude,
                address = record.address,
                stationId = record.stationId,
            )
        }
    }
}
