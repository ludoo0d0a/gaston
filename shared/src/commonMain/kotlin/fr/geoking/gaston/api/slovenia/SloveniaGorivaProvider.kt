package fr.geoking.julius.api.slovenia

import fr.geoking.julius.poi.FuelPrice
import fr.geoking.julius.poi.MapViewport
import fr.geoking.julius.poi.Poi
import fr.geoking.julius.poi.PoiCategory
import fr.geoking.julius.poi.PoiProvider
import fr.geoking.julius.shared.location.haversineKm
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SloveniaGorivaProvider(
    client: HttpClient,
    private val radiusKm: Int = 20,
    private val limit: Int = 50
) : PoiProvider {

    private val gorivaClient = SloveniaGorivaClient(client)
    private val mutex = Mutex()
    private var cachedStations: List<GorivaSIStation>? = null

    private val fuelTypeMap = mapOf(
        "95" to "SP95",
        "dizel" to "Gazole",
        "98" to "SP98",
        "100" to "SP98", // Premuim 100 as SP98
        "dizel-premium" to "Gazole Premium",
        "avtoplin-lpg" to "GPL",
        "cng" to "CNG",
        "lng" to "LNG"
    )

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
                .filter { s ->
                    haversineKm(latitude, longitude, s.lat, s.lng) <= radiusKm
                }
                .map { s ->
                    Poi(
                        id = "goriva:${s.pk}",
                        name = s.name.trim(),
                        address = s.address.trim(),
                        latitude = s.lat,
                        longitude = s.lng,
                        poiCategory = PoiCategory.Gas,
                        fuelPrices = s.prices.mapNotNull { (field, price) ->
                            if (price != null && price > 0) {
                                fuelTypeMap[field]?.let { name ->
                                    FuelPrice(fuelName = name, price = price)
                                }
                            } else null
                        }.ifEmpty { null },
                        source = "Goriva.si (Slovenia)"
                    )
                }
                .take(limit)
                .toList()
        }
    }

    private suspend fun getOrFetchStations(): List<GorivaSIStation> = mutex.withLock {
        cachedStations?.let { return@withLock it }
        val stations = gorivaClient.fetchAllStations()
        cachedStations = stations
        return@withLock stations
    }

    override fun clearCache() {
        cachedStations = null
    }
}
