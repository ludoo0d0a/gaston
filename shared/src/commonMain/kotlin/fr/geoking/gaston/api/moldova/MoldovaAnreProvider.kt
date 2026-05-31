package fr.geoking.gaston.api.moldova

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

class MoldovaAnreProvider(
    client: HttpClient,
    private val radiusKm: Int = 20,
    private val limit: Int = 50
) : PoiProvider {

    private val anreClient = MoldovaAnreClient(client)
    private val mutex = Mutex()
    private var cachedStations: List<ANREStation>? = null

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val stations = getOrFetchStations()
        if (stations.isEmpty()) return emptyList()

        return withContext(Dispatchers.Default) {
            stations.asSequence()
                .filter { s -> s.station_status != 4 }
                .map { s ->
                    val coords = anreClient.webMercatorToLatLon(s.x, s.y)
                    val dist = haversineKm(latitude, longitude, coords.first, coords.second)
                    s to (coords to dist)
                }
                .filter { it.second.second <= radiusKm }
                .map { (s, coordDist) ->
                    val prices = mutableListOf<FuelPrice>()
                    s.gasoline?.takeIf { it > 0 }?.let { prices.add(FuelPrice("SP95", it)) }
                    s.diesel?.takeIf { it > 0 }?.let { prices.add(FuelPrice("Gazole", it)) }
                    s.gpl?.takeIf { it > 0 }?.let { prices.add(FuelPrice("GPL", it)) }

                    Poi(
                        id = "anre:${s.idno}",
                        name = s.station_name.trim().ifEmpty { s.company_name.trim() },
                        address = listOfNotNull(s.fullstreet, s.addrnum).joinToString(" "),
                        latitude = coordDist.first.first,
                        longitude = coordDist.first.second,
                        brand = s.station_name.trim(),
                        poiCategory = PoiCategory.Gas,
                        fuelPrices = prices.ifEmpty { null },
                        source = "ANRE (Moldova)"
                    )
                }
                .take(limit)
                .toList()
        }
    }

    private suspend fun getOrFetchStations(): List<ANREStation> = mutex.withLock {
        cachedStations?.let { return@withLock it }
        return try {
            val s = anreClient.fetchAllStations()
            cachedStations = s
            s
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun clearCache() {
        cachedStations = null
    }
}
