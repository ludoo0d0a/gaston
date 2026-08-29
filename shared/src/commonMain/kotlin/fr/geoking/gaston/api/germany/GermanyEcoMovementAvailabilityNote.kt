package fr.geoking.gaston.api.germany

import fr.geoking.gaston.api.belib.BorneAvailabilityProvider
import fr.geoking.gaston.api.belib.PdcAvailability
import fr.geoking.gaston.parking.ParkingRegion

/**
 * Germany has **no** free national OCPI / open JSON dump comparable to Belgium’s NAP.
 *
 * The German NAP is [Mobilithek](https://mobilithek.info): per-CPO offers over DATEX II
 * (AFIR recharging profile), authenticated with **mTLS client certificates** issued after
 * organisation registration — not an API key header. See `docs/GERMANY_EV_AVAILABILITY.md`.
 *
 * Until a DATEX consumer is viable in-app, Gaston should serve DE EV stations **and**
 * availability via Eco-Movement OCPI when `ECO_MOVEMENT_KEY` is set
 * ([ParkingRegion.Germany]).
 */
object GermanyEcoMovementAvailabilityNote {

    /** Bounding-box region used for country routing (factory / POI resolver). */
    val parkingRegion: ParkingRegion = ParkingRegion.Germany

    /** ISO country code for DE. */
    const val countryCode: String = "DE"

    /**
     * Existing shared availability implementation to prefer for Germany.
     * Wire via [GermanyEcoMovementAvailabilityProvider] or the factory’s Eco-Movement fallback.
     */
    const val recommendedAvailabilityClass: String =
        "fr.geoking.gaston.api.ecomovement.EcoMovementAvailabilityProvider"

    /** Existing shared POI provider for DE EV locations (same key). */
    const val recommendedPoiClass: String =
        "fr.geoking.gaston.api.ecomovement.EcoMovementOcpiProvider"

    /** Build / Settings property for the OCPI token. */
    const val apiKeyProperty: String = "ECO_MOVEMENT_KEY"
}

/**
 * Thin [BorneAvailabilityProvider] marker for [ParkingRegion.Germany] that delegates to
 * Eco-Movement OCPI availability.
 *
 * Does **not** call Mobilithek. Use this when wiring an explicit Germany branch in
 * [fr.geoking.gaston.api.belib.BorneAvailabilityProviderFactory] (snippet in
 * `docs/GERMANY_EV_AVAILABILITY.md`). Today the factory already falls through to Eco-Movement
 * for DE via the `else` branch; this type makes the DE preference discoverable in code.
 */
class GermanyEcoMovementAvailabilityProvider(
    private val ecoMovement: BorneAvailabilityProvider,
) : BorneAvailabilityProvider {

    override suspend fun getAvailability(
        latitude: Double,
        longitude: Double,
        radiusKm: Int,
    ): List<PdcAvailability> =
        ecoMovement.getAvailability(latitude, longitude, radiusKm)
}
