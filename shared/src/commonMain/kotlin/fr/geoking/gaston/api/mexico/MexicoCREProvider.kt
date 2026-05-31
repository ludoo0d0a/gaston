package fr.geoking.gaston.api.mexico

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

class MexicoCREProvider(
    client: HttpClient,
    private val radiusKm: Int = 20,
    private val limit: Int = 50
) : PoiProvider {

    private val creClient = MexicoCREClient(client)
    private val mutex = Mutex()
    private var cachedPlaces: List<MexicoPlace>? = null
    private var cachedPrices: List<MexicoPrice>? = null

    private val fuelMap = mapOf(
        "regular" to "SP95",
        "premium" to "SP95 Premium",
        "diesel" to "Gazole"
    )

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val places = getOrFetchPlaces()
        val prices = getOrFetchPrices()
        if (places.isEmpty() || prices.isEmpty()) return emptyList()

        val priceMap = prices.groupBy { it.placeId }

        return withContext(Dispatchers.Default) {
            places.asSequence()
                .filter { p ->
                    haversineKm(latitude, longitude, p.lat, p.lon) <= radiusKm
                }
                .mapNotNull { p ->
                    val stationPrices = priceMap[p.id]?.mapNotNull { sp ->
                        fuelMap[sp.type]?.let { name ->
                            FuelPrice(name, sp.price)
                        }
                    } ?: return@mapNotNull null

                    Poi(
                        id = "cre:${p.id}",
                        name = p.name.ifEmpty { "Gas Station" },
                        address = "",
                        latitude = p.lat,
                        longitude = p.lon,
                        brand = extractBrand(p.name),
                        poiCategory = PoiCategory.Gas,
                        fuelPrices = stationPrices,
                        source = "Comisión Reguladora de Energía (Mexico)"
                    )
                }
                .take(limit)
                .toList()
        }
    }

    private suspend fun getOrFetchPlaces(): List<MexicoPlace> = mutex.withLock {
        cachedPlaces?.let { return@withLock it }
        return try {
            val p = creClient.fetchPlaces()
            cachedPlaces = p
            p
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun getOrFetchPrices(): List<MexicoPrice> = mutex.withLock {
        cachedPrices?.let { return@withLock it }
        return try {
            val p = creClient.fetchPrices()
            cachedPrices = p
            p
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun extractBrand(name: String): String? {
        val brands = listOf("PEMEX", "SHELL", "BP", "MOBIL", "TOTAL", "OXXO GAS", "G500")
        val upper = name.uppercase()
        return brands.find { upper.contains(it) }?.let { brand ->
            when(brand) {
                "TOTAL" -> "TotalEnergies"
                else -> brand.lowercase().replaceFirstChar { it.uppercase() }
            }
        }
    }

    override suspend fun clearCache() {
        cachedPlaces = null
        cachedPrices = null
    }
}
