package fr.geoking.gaston.api.spain

import fr.geoking.gaston.api.belib.BorneAvailabilityProvider
import fr.geoking.gaston.api.belib.PdcAvailability
import fr.geoking.gaston.parking.ParkingRegion

/**
 * Spain has **no** documented public consumer pull API for live EVSE availability
 * comparable to Belgium’s NAP OCPI dump or QualiCharge dynamique.
 *
 * Official stack (probed 2026-08-29):
 * - **RIPREE** (MITECO) — CPO static registry / citizen portals
 * - **SGV** (Red Eléctrica) — CPOs **push** OCPI 2.1.1 / 2.2.1 (Locations, Tariffs, status)
 * - **REVE** — [mapareve.es](https://www.mapareve.es/) human map + apps; no open pull API
 * - **DGT NAP** — DATEX II v3 `EnergyInfrastructureTablePublication` (static inventory, ~24h);
 *   no public `EnergyInfrastructureStatusPublication` sibling (404)
 *
 * Until REVE/SGV or the NAP expose a free consumer status pull, Gaston should serve ES
 * EV availability via Eco-Movement OCPI when `ECO_MOVEMENT_KEY` is set
 * ([ParkingRegion.Spain]). See `docs/SPAIN_EV_AVAILABILITY.md`.
 */
object SpainEcoMovementAvailabilityNote {

    /** Bounding-box region used for country routing (factory / POI resolver). */
    val parkingRegion: ParkingRegion = ParkingRegion.Spain

    /** ISO country code for ES. */
    const val countryCode: String = "ES"

    /**
     * Existing shared availability implementation to prefer for Spain.
     * Wire via [SpainEcoMovementAvailabilityProvider] or the factory’s Eco-Movement fallback.
     */
    const val recommendedAvailabilityClass: String =
        "fr.geoking.gaston.api.ecomovement.EcoMovementAvailabilityProvider"

    /** Existing shared POI provider for ES EV locations (same key). */
    const val recommendedPoiClass: String =
        "fr.geoking.gaston.api.ecomovement.EcoMovementOcpiProvider"

    /** Build / Settings property for the OCPI token. */
    const val apiKeyProperty: String = "ECO_MOVEMENT_KEY"
}

/**
 * Thin [BorneAvailabilityProvider] marker for [ParkingRegion.Spain] that delegates to
 * Eco-Movement OCPI availability.
 *
 * Does **not** call REVE, SGV, or DGT DATEX. Use when wiring an explicit Spain branch in
 * [fr.geoking.gaston.api.belib.BorneAvailabilityProviderFactory] (snippet in
 * `docs/SPAIN_EV_AVAILABILITY.md`). Today the factory already falls through to Eco-Movement
 * for ES via the `else` branch; this type makes the ES preference discoverable in code.
 */
class SpainEcoMovementAvailabilityProvider(
    private val ecoMovement: BorneAvailabilityProvider,
) : BorneAvailabilityProvider {

    override suspend fun getAvailability(
        latitude: Double,
        longitude: Double,
        radiusKm: Int,
    ): List<PdcAvailability> =
        ecoMovement.getAvailability(latitude, longitude, radiusKm)
}
