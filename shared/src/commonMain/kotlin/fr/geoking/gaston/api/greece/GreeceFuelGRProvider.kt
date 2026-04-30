package fr.geoking.gaston.api.greece

import fr.geoking.gaston.poi.FuelPrice
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProvider
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GreeceFuelGRProvider(
    client: HttpClient,
    private val limit: Int = 50
) : PoiProvider {

    private val fuelGRClient = GreeceFuelGRClient(client)

    // Map FuelGR fuel type parameters to Julius fuel names
    private val fuelQueries = mapOf(
        "1" to "SP95",
        "4" to "Gazole",
        "2" to "SP98",
        "6" to "GPL"
    )

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        // In the interest of efficiency for a mobile provider, we'll only query the primary fuel types
        // (SP95 and Gazole) if no specific filters are applied, or we could query them all.
        // For simplicity and matching Gaston's 30km radius, we'll fetch them.

        val stationMap = mutableMapOf<String, Poi>()

        return withContext(Dispatchers.Default) {
            fuelQueries.forEach { (f, fuelName) ->
                try {
                    val results = fuelGRClient.fetchNearbyStations(latitude, longitude, f)
                    results.forEach { s ->
                        val poi = stationMap.getOrPut(s.id) {
                            Poi(
                                id = "fuelgr:${s.id}",
                                name = s.brand.ifEmpty { "Gas Station" },
                                address = s.address ?: "",
                                latitude = s.lat,
                                longitude = s.lng,
                                brand = s.brand,
                                poiCategory = PoiCategory.Gas,
                                fuelPrices = mutableListOf(),
                                source = "FuelGR (Greece)"
                            )
                        }
                        (poi.fuelPrices as MutableList).add(FuelPrice(fuelName, s.price))
                    }
                } catch (e: Exception) {
                    // Ignore errors for individual fuel type queries
                }
            }
            stationMap.values.take(limit).toList()
        }
    }
}
