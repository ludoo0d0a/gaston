package fr.geoking.gaston.api.netherlands

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

class NetherlandsAnwbProvider(
    client: HttpClient,
    private val radiusKm: Int = 20,
    private val limit: Int = 50
) : PoiProvider {

    private val anwbClient = NetherlandsAnwbClient(client)
    private val mutex = Mutex()
    private val cachedStations = mutableMapOf<String, List<ANWBStation>>()

    private val fuelTypeMap = mapOf(
        "EURO95" to "SP95",
        "EURO98" to "SP98",
        "DIESEL" to "Gazole",
        "DIESEL_SPECIAL" to "Gazole Premium",
        "AUTOGAS" to "GPL",
        "CNG" to "CNG"
    )

    private val nlBbox = "50.7,3.3,53.6,7.3"
    private val luBbox = "49.4,5.7,50.2,6.6"
    private val beBbox = "49.4,2.4,51.6,6.5"

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val bbox = when {
            latitude in 49.4..50.2 && longitude in 5.7..6.6 -> luBbox
            latitude in 49.4..51.6 && longitude in 2.4..6.5 -> beBbox
            latitude in 50.7..53.6 && longitude in 3.3..7.3 -> nlBbox
            else -> return emptyList()
        }

        val stations = getOrFetchStations(bbox)
        if (stations.isEmpty()) return emptyList()

        return withContext(Dispatchers.Default) {
            stations.asSequence()
                .filter { s ->
                    haversineKm(latitude, longitude, s.coordinates.latitude, s.coordinates.longitude) <= radiusKm
                }
                .map { s ->
                    Poi(
                        id = "anwb:${s.id}",
                        name = s.title.trim(),
                        address = s.address?.streetAddress?.trim() ?: "",
                        latitude = s.coordinates.latitude,
                        longitude = s.coordinates.longitude,
                        brand = s.title.split(" ").firstOrNull(),
                        poiCategory = PoiCategory.Gas,
                        fuelPrices = s.prices?.mapNotNull { p ->
                            fuelTypeMap[p.fuelType]?.let { name ->
                                FuelPrice(fuelName = name, price = p.value)
                            }
                        }?.ifEmpty { null },
                        source = "ANWB"
                    )
                }
                .take(limit)
                .toList()
        }
    }

    private suspend fun getOrFetchStations(bbox: String): List<ANWBStation> = mutex.withLock {
        cachedStations[bbox]?.let { return@withLock it }
        return try {
            val stations = anwbClient.fetchStations(bbox)
            cachedStations[bbox] = stations
            stations
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun clearCache() {
        cachedStations.clear()
    }
}
