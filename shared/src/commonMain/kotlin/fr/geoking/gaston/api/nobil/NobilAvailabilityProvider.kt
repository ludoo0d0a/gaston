package fr.geoking.gaston.api.nobil

import fr.geoking.gaston.api.belib.BorneAvailabilityProvider
import fr.geoking.gaston.api.belib.PdcAvailability

/**
 * [BorneAvailabilityProvider] backed by the NOBIL datadump (status embedded per connector when
 * reported). Default country is Norway (`NOR`); pass `SWE` for Sweden via [NobilClient].
 */
class NobilAvailabilityProvider(
    private val client: NobilClient,
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
