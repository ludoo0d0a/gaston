package fr.geoking.gaston.api.ireland

import fr.geoking.gaston.api.belib.BorneAvailabilityProvider
import fr.geoking.gaston.api.belib.PdcAvailability
import fr.geoking.gaston.parking.ParkingRegion

/**
 * Ireland has **no** free national OCPI / open JSON dump on [data.gov.ie](https://data.gov.ie)
 * comparable to Belgium’s NAP or Netherlands DOT-NL.
 *
 * TII’s Data Exchange Platform (DXP) ingests CPO data over **OCPI 2.2.1** and is meant to
 * become discoverable via the Irish NAP (data.gov.ie). As of the 2026-08-29 probe, no OCPI
 * locations resource is published there; guessed `dxp.tii.ie` hosts return **502**. See
 * `docs/IRELAND_TII_AVAILABILITY.md`.
 *
 * Until a NAP consumer pull URL exists, Gaston should serve IE EV stations **and**
 * availability via Eco-Movement OCPI when `ECO_MOVEMENT_KEY` is set
 * ([ParkingRegion.Ireland]). Fuel POIs remain [IrelandPickAPumpProvider].
 */
object IrelandTiiEcoMovementAvailabilityNote {

    /** Bounding-box region used for country routing (factory / POI resolver). */
    val parkingRegion: ParkingRegion = ParkingRegion.Ireland

    /** ISO country code for IE. */
    const val countryCode: String = "IE"

    /**
     * Existing shared availability implementation to prefer for Ireland.
     * Wire via [IrelandTiiEcoMovementAvailabilityProvider] or the factory’s Eco-Movement fallback.
     */
    const val recommendedAvailabilityClass: String =
        "fr.geoking.gaston.api.ecomovement.EcoMovementAvailabilityProvider"

    /** Existing shared POI provider for IE EV locations (same key). */
    const val recommendedPoiClass: String =
        "fr.geoking.gaston.api.ecomovement.EcoMovementOcpiProvider"

    /** Build / Settings property for the OCPI token. */
    const val apiKeyProperty: String = "ECO_MOVEMENT_KEY"

    /** TII Data Office — AFIR / DXP consumer publication questions. */
    const val tiiAfirContact: String = "AFIRdata@tii.ie"
}

/**
 * Thin [BorneAvailabilityProvider] marker for [ParkingRegion.Ireland] that delegates to
 * Eco-Movement OCPI availability.
 *
 * Does **not** call TII DXP / data.gov.ie. Use when wiring an explicit Ireland branch in
 * [fr.geoking.gaston.api.belib.BorneAvailabilityProviderFactory] (snippet in
 * `docs/IRELAND_TII_AVAILABILITY.md`). Today the factory already falls through to Eco-Movement
 * for IE via the `else` branch; this type makes the IE preference discoverable in code.
 */
class IrelandTiiEcoMovementAvailabilityProvider(
    private val ecoMovement: BorneAvailabilityProvider,
) : BorneAvailabilityProvider {

    override suspend fun getAvailability(
        latitude: Double,
        longitude: Double,
        radiusKm: Int,
    ): List<PdcAvailability> =
        ecoMovement.getAvailability(latitude, longitude, radiusKm)
}
