package fr.geoking.gaston.api.nobil

import fr.geoking.gaston.api.belib.BorneAvailabilityProvider
import fr.geoking.gaston.api.belib.PdcAvailability

/**
 * Sweden-scoped [BorneAvailabilityProvider] via the shared [NobilClient]
 * (`countrycode=SWE` — Energimyndigheten / Swedish AFIR NAP).
 *
 * Construct [NobilClient] with [COUNTRY_CODE] (or [NobilClient.COUNTRY_SWE]).
 */
class SwedenNobilAvailabilityProvider(
    client: NobilClient,
    radiusKm: Int = 15,
    limit: Int = 200,
) : BorneAvailabilityProvider {

    private val delegate = NobilAvailabilityProvider(
        client = client,
        radiusKm = radiusKm,
        limit = limit,
    )

    override suspend fun getAvailability(
        latitude: Double,
        longitude: Double,
        radiusKm: Int,
    ): List<PdcAvailability> = delegate.getAvailability(latitude, longitude, radiusKm)

    companion object {
        /** NOBIL datadump `countrycode` for Sweden. */
        const val COUNTRY_CODE: String = NobilClient.COUNTRY_SWE
    }
}
