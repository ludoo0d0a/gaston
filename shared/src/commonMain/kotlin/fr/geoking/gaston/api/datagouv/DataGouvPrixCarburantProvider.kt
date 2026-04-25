package fr.geoking.gaston.api.datagouv

import fr.geoking.gaston.poi.FuelPrice
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.api.routex.radiusKmFromMapViewport
import fr.geoking.gaston.poi.PoiProvider
import io.ktor.client.HttpClient

/**
 * [PoiProvider] for French fuel open data **Prix des carburants en France** (flux instantané v2),
 * fetched by [DataGouvPrixCarburantClient].
 *
 * **How the names relate (same underlying public data, different entry points):**
 * - **[prix-carburants.gouv.fr](https://www.prix-carburants.gouv.fr/)** — official price-information system
 *   (DGCCRF); what motorists see on the web.
 * - **[data.gouv.fr](https://www.data.gouv.fr)** — national open-data portal; catalogues the dataset and
 *   links to the API; HTTP calls for this feed go to the Economy portal below, not to data.gouv.fr itself.
 * - **[data.economie.gouv.fr](https://data.economie.gouv.fr)** — Explore API host used in code
 *   (`prix-des-carburants-en-france-flux-instantane-v2`).
 * - **[donnees.roulez-eco.fr](https://donnees.roulez-eco.fr)** — bulk open-data exports (e.g. instant ZIP)
 *   that mirror the same system.
 * - **Etalab** — the dataset is published under **Licence Ouverte 2.0 (Etalab)**; “Etalab” in older code or
 *   docs referred to this licence/distribution lineage, not a separate API product.
 *
 * Refreshed about every 10 minutes. No API key (open data).
 */
class DataGouvPrixCarburantProvider(
    private val client: HttpClient,
    private val radiusKm: Int = 10,
    private val limit: Int = 500
) : PoiProvider {

    private val prixCarburantClient = DataGouvPrixCarburantClient(client)

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val effectiveRadiusKm = viewport
            ?.let {
                radiusKmFromMapViewport(latitude, longitude, it.zoom, it.mapWidthPx, it.mapHeightPx)
                    .coerceIn(1, 50)
            }
            ?: radiusKm

        val stations = prixCarburantClient.getStations(latitude, longitude, effectiveRadiusKm, limit)
        return stations.map { station ->
            Poi(
                id = station.id,
                name = station.name,
                address = station.address,
                latitude = station.latitude,
                longitude = station.longitude,
                brand = station.brand,
                fuelPrices = station.fuels.map { p ->
                    FuelPrice(
                        fuelName = p.name,
                        price = p.priceEur,
                        updatedAt = null,
                        outOfStock = false
                    )
                }.ifEmpty { null },
                source = "DataGouvPrixCarburant"
            )
        }
    }
}
