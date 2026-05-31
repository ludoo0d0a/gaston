package fr.geoking.gaston.api.australia

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

class AustraliaNswFuelCheckProvider(
    client: HttpClient,
    private val apiKey: String,
    private val apiSecret: String,
    private val radiusKm: Int = 20,
    private val limit: Int = 50
) : PoiProvider {

    private val fuelCheckClient = AustraliaNswFuelCheckClient(client)
    private val mutex = Mutex()
    private var cachedData: NSWResponse? = null

    private val fuelMap = mapOf(
        "U91" to "SP95",
        "E10" to "E10",
        "P95" to "SP95 Premium",
        "P98" to "SP98",
        "DL" to "Gazole",
        "PDL" to "Gazole Premium",
        "LPG" to "GPL"
    )

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val data = getOrFetchData() ?: return emptyList()

        val stationPrices = data.prices.groupBy { it.stationcode }

        return withContext(Dispatchers.Default) {
            data.stations.asSequence()
                .filter { s ->
                    haversineKm(latitude, longitude, s.location.latitude, s.location.longitude) <= radiusKm
                }
                .mapNotNull { s ->
                    val prices = stationPrices[s.code]?.mapNotNull { p ->
                        fuelMap[p.fueltype]?.let { name ->
                            FuelPrice(name, p.price / 100.0)
                        }
                    } ?: return@mapNotNull null

                    Poi(
                        id = "nsw:${s.code}",
                        name = s.name.ifEmpty { "${s.brand} ${s.code}" },
                        address = s.address,
                        latitude = s.location.latitude,
                        longitude = s.location.longitude,
                        brand = s.brand,
                        poiCategory = PoiCategory.Gas,
                        fuelPrices = prices,
                        source = "FuelCheck (NSW Australia)"
                    )
                }
                .take(limit)
                .toList()
        }
    }

    private suspend fun getOrFetchData(): NSWResponse? = mutex.withLock {
        if (apiKey.isEmpty() || apiSecret.isEmpty()) return@withLock null
        cachedData?.let { return@withLock it }
        return try {
            val d = fuelCheckClient.fetchAllData(apiKey, apiSecret)
            cachedData = d
            d
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun clearCache() {
        cachedData = null
    }
}
