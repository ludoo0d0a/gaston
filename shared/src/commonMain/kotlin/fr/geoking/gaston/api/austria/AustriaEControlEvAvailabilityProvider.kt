package fr.geoking.gaston.api.austria

import fr.geoking.gaston.api.belib.BorneAvailabilityProvider
import fr.geoking.gaston.api.belib.PdcAvailability

/**
 * [BorneAvailabilityProvider] for Austria E-Control Ladestellenverzeichnis
 * (charge API `/search` with live per-EVSE status).
 *
 * Does not touch fuel Sprit ([fr.geoking.gaston.api.econtrol.AustriaEControlProvider]).
 */
class AustriaEControlEvAvailabilityProvider(
    private val client: AustriaEControlEvClient,
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
