package fr.geoking.gaston.api.denmark

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

class FuelpricesDKProvider(
    client: HttpClient,
    private val apiKey: String,
    private val radiusKm: Int = 20,
    private val limit: Int = 50
) : PoiProvider {

    private val dkClient = FuelpricesDKClient(client)
    private val mutex = Mutex()
    private var cachedStations: List<DKStationWithPrices>? = null

    private val fuelProductMap = mapOf(
        "blyfri 95" to "SP95",
        "blyfri 95 e10" to "SP95",
        "blyfri 98" to "SP98",
        "oktan 95" to "SP95",
        "oktan 95 e10" to "SP95",
        "oktan 100" to "SP95 Premium",
        "diesel" to "Gazole",
        "diesel+" to "Gazole Premium",
        "dieselplus" to "Gazole Premium"
    )

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        if (apiKey.isEmpty()) return emptyList()
        val stations = getOrFetchStations()
        if (stations.isEmpty()) return emptyList()

        return withContext(Dispatchers.Default) {
            stations.asSequence()
                .filter { entry ->
                    val s = entry.station
                    s.latitude != null && s.longitude != null &&
                    haversineKm(latitude, longitude, s.latitude, s.longitude) <= radiusKm
                }
                .map { entry ->
                    val s = entry.station
                    val prices = entry.prices.mapNotNull { (product, priceStr) ->
                        val price = priceStr.toDoubleOrNull() ?: return@mapNotNull null
                        if (price > 0) {
                            fuelProductMap[product.lowercase()]?.let { name ->
                                FuelPrice(fuelName = name, price = price)
                            }
                        } else null
                    }

                    Poi(
                        id = "dk-fp-${entry.company.id}-${s.id}",
                        name = s.name.trim().ifEmpty { "${entry.company.company} ${s.address}".trim() },
                        address = s.address.trim(),
                        latitude = s.latitude!!,
                        longitude = s.longitude!!,
                        brand = entry.company.company.trim(),
                        poiCategory = PoiCategory.Gas,
                        fuelPrices = prices.ifEmpty { null },
                        source = "Fuelprices.dk"
                    )
                }
                .filter { it.fuelPrices != null }
                .take(limit)
                .toList()
        }
    }

    private suspend fun getOrFetchStations(): List<DKStationWithPrices> = mutex.withLock {
        cachedStations?.let { return@withLock it }
        return try {
            val stations = dkClient.fetchAllStations(apiKey)
            cachedStations = stations
            stations
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun clearCache() {
        cachedStations = null
    }
}
