package fr.geoking.gaston.api.gas

import fr.geoking.gaston.api.routex.radiusKmFromMapViewport
import fr.geoking.gaston.poi.FuelPrice
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProvider
import io.ktor.client.HttpClient

/**
 * [PoiProvider] implementation that fetches gas stations and fuel prices from the
 * Gas API (gas-api.ovh), which uses French government open data from
 * [data.gouv.fr](https://www.data.gouv.fr/reuses/gas-api) / [prix-carburants.gouv.fr](https://www.prix-carburants.gouv.fr/).
 *
 * No API key required. Returns [Poi] with [Poi.fuelPrices] populated when available.
 */
class GasApiProvider(
    private val client: HttpClient,
    private val radiusKm: Int = 10,
    private val limit: Int = 100
) : PoiProvider {

    private val gasApiClient = GasApiClient(client)

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

        val stations = gasApiClient.searchStations(
            latitude = latitude,
            longitude = longitude,
            radiusKm = effectiveRadiusKm,
            limit = limit
        )
        return stations.map { station ->
            Poi(
                id = station.id,
                name = station.name,
                address = station.address,
                latitude = station.latitude,
                longitude = station.longitude,
                brand = station.brand,
                fuelPrices = station.prices.map { p ->
                    FuelPrice(
                        fuelName = p.fuelName,
                        price = p.price,
                        updatedAt = p.updatedAt,
                        outOfStock = p.outOfStock
                    )
                }.ifEmpty { null },
                source = "GasAPI"
            )
        }
    }
}
