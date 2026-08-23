package fr.geoking.gaston.api.datagouv

import fr.geoking.gaston.poi.FuelPrice
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.radiusKmFromMapViewport
import fr.geoking.gaston.poi.PoiProvider
import io.ktor.client.HttpClient

/**
 * [PoiProvider] implementation that fetches gas stations and fuel prices from the French
 * open data "Prix des carburants en France - Flux quotidien" (data.economie.gouv.fr),
 * dataset [prix-carburants-quotidien].
 *
 * Uses [DataGouvClient] for locations and prices. Data is updated daily (J-1).
 * No API key required. Returns [Poi] with [Poi.fuelPrices] populated.
 *
 * API: https://data.economie.gouv.fr/explore/dataset/prix-carburants-quotidien/api/
 */
class DataGouvProvider(
    private val client: HttpClient,
    private val radiusKm: Int = 10,
    private val limit: Int = 500
) : PoiProvider {

    private val dataGouvClient = DataGouvClient(client)

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

        val stations = dataGouvClient.getStations(
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
                isOnHighway = station.isOnHighway,
                fuelPrices = station.prices.map { p ->
                    FuelPrice(
                        fuelName = p.fuelName,
                        price = p.price,
                        updatedAt = p.updatedAt,
                        outOfStock = p.outOfStock
                    )
                }.ifEmpty { null },
                source = "DataGouv"
            )
        }
    }
}
