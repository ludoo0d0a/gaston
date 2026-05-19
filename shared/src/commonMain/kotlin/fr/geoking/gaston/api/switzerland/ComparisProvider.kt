package fr.geoking.gaston.api.switzerland

import fr.geoking.gaston.poi.FuelPrice
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.shared.location.haversineKm
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ComparisProvider(
    client: HttpClient,
    private val radiusKm: Int = 25,
    private val limit: Int = 80,
) : PoiProvider {

    private val comparisClient = ComparisClient(client)
    private val mutex = Mutex()
    private var cachedStations: List<ComparisStation>? = null

    private val fuelNameMap = mapOf(
        "BENZIN" to "SP95",
        "DIESEL" to "Gazole",
        "SUPER" to "SP98",
        "SUPERPLUS" to "SP98 Premium",
        "GAS" to "GPL",
    )

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?,
    ): List<Poi> {
        if (latitude < 45.8 || latitude > 47.8 || longitude < 5.9 || longitude > 10.5) {
            return emptyList()
        }
        val stations = getOrFetchStations()
        if (stations.isEmpty()) return emptyList()

        return withContext(Dispatchers.Default) {
            stations.asSequence()
                .filter { s -> haversineKm(latitude, longitude, s.lat, s.lng) <= radiusKm }
                .map { s ->
                    Poi(
                        id = "comparis:${s.id}",
                        name = s.name.ifBlank { s.brand ?: "Gas station" },
                        address = s.address,
                        latitude = s.lat,
                        longitude = s.lng,
                        brand = s.brand,
                        poiCategory = PoiCategory.Gas,
                        fuelPrices = s.prices.mapNotNull { p ->
                            val name = fuelNameMap[p.fuelName] ?: p.fuelName
                            FuelPrice(name, p.price)
                        }.ifEmpty { null },
                        source = "Comparis (Switzerland)",
                    )
                }
                .take(limit)
                .toList()
        }
    }

    private suspend fun getOrFetchStations(): List<ComparisStation> = mutex.withLock {
        cachedStations?.let { return@withLock it }
        return try {
            val stations = comparisClient.fetchAllStations()
            cachedStations = stations
            stations
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun clearCache() {
        cachedStations = null
    }
}
