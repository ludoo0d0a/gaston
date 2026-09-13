package fr.geoking.gaston.api.gireve

import fr.geoking.gaston.api.belib.BorneAvailabilityProvider
import fr.geoking.gaston.api.belib.PdcAvailability

/**
 * [BorneAvailabilityProvider] implementation for Gireve IRVE dynamique (France),
 * via transport.data.gouv.fr proxy CSV feeds.
 */
class GireveAvailabilityProvider(
    private val client: GireveClient,
    private val radiusKm: Int = 15,
    private val limit: Int = 300
) : BorneAvailabilityProvider {

    override suspend fun getAvailability(
        latitude: Double,
        longitude: Double,
        radiusKm: Int
    ): List<PdcAvailability> {
        val effectiveRadius = if (radiusKm > 0) radiusKm else this.radiusKm
        val records = client.getAvailability(
            latitude = latitude,
            longitude = longitude,
            radiusKm = effectiveRadius,
            limit = limit
        )
        return records.map { record ->
            PdcAvailability(
                id = record.idPdcItinerance,
                status = client.mapStatus(record.etatPdc, record.occupationPdc),
                latitude = record.latitude,
                longitude = record.longitude,
                stationId = record.stationId
            )
        }
    }
}
