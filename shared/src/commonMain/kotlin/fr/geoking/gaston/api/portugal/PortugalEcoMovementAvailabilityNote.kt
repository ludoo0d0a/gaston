package fr.geoking.gaston.api.portugal

import fr.geoking.gaston.api.belib.BorneAvailabilityProvider
import fr.geoking.gaston.api.belib.PdcAvailability
import fr.geoking.gaston.parking.ParkingRegion

/**
 * Portugal’s AFIR path is **Mobi.E as EADME → IMT NAP (PAN)**.
 *
 * As of 2026-08-29 the NAP catalogue lists open DATEX II pull URLs for national
 * static + dynamic EV data (`ev-nap.mobie.pt`), but the payloads are bulk XML
 * (~190 MB static / ~40 MB status) — not suitable for direct in-app viewport
 * fetches. Partner OCPI on `pgm.mobie.pt` requires CPO/CEME credentials.
 * See `docs/PORTUGAL_EV_AVAILABILITY.md`.
 *
 * Until a streaming/filtered DATEX consumer (or a thinner national API) exists,
 * Gaston should serve PT EV stations **and** availability via Eco-Movement OCPI
 * when `ECO_MOVEMENT_KEY` is set ([ParkingRegion.Portugal]).
 */
object PortugalEcoMovementAvailabilityNote {

    /** Bounding-box region used for country routing (factory / POI resolver). */
    val parkingRegion: ParkingRegion = ParkingRegion.Portugal

    /** ISO country code for PT. */
    const val countryCode: String = "PT"

    /**
     * Existing shared availability implementation to prefer for Portugal.
     * Wire via [PortugalEcoMovementAvailabilityProvider] or the factory’s Eco-Movement fallback.
     */
    const val recommendedAvailabilityClass: String =
        "fr.geoking.gaston.api.ecomovement.EcoMovementAvailabilityProvider"

    /** Existing shared POI provider for PT EV locations (same key). */
    const val recommendedPoiClass: String =
        "fr.geoking.gaston.api.ecomovement.EcoMovementOcpiProvider"

    /** Build / Settings property for the OCPI token. */
    const val apiKeyProperty: String = "ECO_MOVEMENT_KEY"

    /** IMT NAP catalogue entry — static MOBI.E network (DATEX II). */
    const val napStaticSupplyId: Int = 148

    /** IMT NAP catalogue entry — dynamic MOBI.E status (DATEX II). */
    const val napDynamicSupplyId: Int = 149

    /** Open DATEX II static table (EnergyInfrastructureTablePublication). */
    const val datexStaticUrl: String =
        "https://ev-nap.mobie.pt/integration/nap/evChargingInfra"

    /** Open DATEX II status publication (EnergyInfrastructureStatusPublication). */
    const val datexStatusUrl: String =
        "https://ev-nap.mobie.pt/integration/nap/evActualStatus"
}

/**
 * Thin [BorneAvailabilityProvider] marker for [ParkingRegion.Portugal] that delegates to
 * Eco-Movement OCPI availability.
 *
 * Does **not** download MOBI.E DATEX dumps. Use this when wiring an explicit Portugal branch in
 * [fr.geoking.gaston.api.belib.BorneAvailabilityProviderFactory] (snippet in
 * `docs/PORTUGAL_EV_AVAILABILITY.md`). Today the factory already falls through to Eco-Movement
 * for PT via the `else` branch; this type makes the PT preference discoverable in code.
 */
class PortugalEcoMovementAvailabilityProvider(
    private val ecoMovement: BorneAvailabilityProvider,
) : BorneAvailabilityProvider {

    override suspend fun getAvailability(
        latitude: Double,
        longitude: Double,
        radiusKm: Int,
    ): List<PdcAvailability> =
        ecoMovement.getAvailability(latitude, longitude, radiusKm)
}
