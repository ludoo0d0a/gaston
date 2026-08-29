package fr.geoking.gaston.api.denmark

import fr.geoking.gaston.api.belib.BorneAvailabilityProvider
import fr.geoking.gaston.api.belib.PdcAvailability
import fr.geoking.gaston.parking.ParkingRegion

/**
 * Denmark has **no** free national OCPI / open JSON dump comparable to Belgium’s NAP.
 *
 * The Danish NAP is [Dataudveksleren](https://nap.vd.dk/) (Vejdirektoratet): AFIR Art. 20
 * listings (often DATEX II “reference” URLs or Eco-Movement aggregated DATEX). Pulls require
 * a logged-in portal user and a **servicekonto** (HTTP Basic) — anonymous distribution URLs
 * return **401**. See `docs/DENMARK_EV_AVAILABILITY.md`.
 *
 * Klimadatastyrelsen’s “Ladepunktsdata i realtid” aggregates CPO OCPI for a national overview;
 * a consumer API is mandated in regulation but **not** documented as an open app pull today.
 *
 * Until a DATEX/NAP or KDS consumer is viable in-app, Gaston should serve DK EV stations
 * **and** availability via Eco-Movement OCPI when `ECO_MOVEMENT_KEY` is set
 * ([ParkingRegion.Denmark]).
 */
object DenmarkEcoMovementAvailabilityNote {

    /** Bounding-box region used for country routing (factory / POI resolver). */
    val parkingRegion: ParkingRegion = ParkingRegion.Denmark

    /** ISO country code for DK. */
    const val countryCode: String = "DK"

    /**
     * Existing shared availability implementation to prefer for Denmark.
     * Wire via [DenmarkEcoMovementAvailabilityProvider] or the factory’s Eco-Movement fallback.
     */
    const val recommendedAvailabilityClass: String =
        "fr.geoking.gaston.api.ecomovement.EcoMovementAvailabilityProvider"

    /** Existing shared POI provider for DK EV locations (same key). */
    const val recommendedPoiClass: String =
        "fr.geoking.gaston.api.ecomovement.EcoMovementOcpiProvider"

    /** Build / Settings property for the OCPI token. */
    const val apiKeyProperty: String = "ECO_MOVEMENT_KEY"

    /**
     * Aggregated DATEX II catalogue entry on Dataudveksleren (Eco-Movement publisher).
     * Metadata is public; binary/XML pull still needs a service account.
     */
    const val napAggregatedDatexDatasetUrl: String =
        "https://du-portal-ui.dataudveksler.app.vd.dk/data/950/overview"

    /** Distribution pull pattern (returns HTTP 401 without servicekonto credentials). */
    const val napDistributionUrlPattern: String =
        "https://distribution.dataudveksler.app.vd.dk/api/dataset/{id}/latest/DatexII"
}

/**
 * Thin [BorneAvailabilityProvider] marker for [ParkingRegion.Denmark] that delegates to
 * Eco-Movement OCPI availability.
 *
 * Does **not** call Dataudveksleren / DATEX or Klimadatastyrelsen. Use this when wiring an
 * explicit Denmark branch in
 * [fr.geoking.gaston.api.belib.BorneAvailabilityProviderFactory] (snippet in
 * `docs/DENMARK_EV_AVAILABILITY.md`). Today the factory already falls through to Eco-Movement
 * for DK via the `else` branch; this type makes the DK preference discoverable in code.
 */
class DenmarkEcoMovementAvailabilityProvider(
    private val ecoMovement: BorneAvailabilityProvider,
) : BorneAvailabilityProvider {

    override suspend fun getAvailability(
        latitude: Double,
        longitude: Double,
        radiusKm: Int,
    ): List<PdcAvailability> =
        ecoMovement.getAvailability(latitude, longitude, radiusKm)
}
