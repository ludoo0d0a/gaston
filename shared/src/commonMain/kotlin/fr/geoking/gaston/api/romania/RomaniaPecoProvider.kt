package fr.geoking.gaston.api.romania

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

class RomaniaPecoProvider(
    client: HttpClient,
    private val radiusKm: Int = 20,
    private val limit: Int = 50
) : PoiProvider {

    private val pecoClient = RomaniaPecoClient(client)
    private val mutex = Mutex()
    private var cachedStations: List<PecoStation>? = null

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
                    val prices = mutableListOf<FuelPrice>()
                    s.Benzina_Regular?.takeIf { it > 0 && it < 999999 }?.let { prices.add(FuelPrice("SP95", it)) }
                    s.Benzina_Premium?.takeIf { it > 0 && it < 999999 }?.let { prices.add(FuelPrice("SP98", it)) }
                    s.Motorina_Regular?.takeIf { it > 0 && it < 999999 }?.let { prices.add(FuelPrice("Gazole", it)) }
                    s.Motorina_Premium?.takeIf { it > 0 && it < 999999 }?.let { prices.add(FuelPrice("Gazole Premium", it)) }
                    s.GPL?.takeIf { it > 0 && it < 999999 }?.let { prices.add(FuelPrice("GPL", it)) }
                    s.AdBlue?.takeIf { it > 0 && it < 999999 }?.let { prices.add(FuelPrice("AdBlue", it)) }

                    Poi(
                        id = "peco:${s.Id ?: s.objectId}",
                        name = s.Statie?.trim() ?: s.Retea?.trim() ?: "Gas Station",
                        address = s.Adresa?.trim() ?: "",
                        latitude = s.lat,
                        longitude = s.lng,
                        brand = s.Retea?.trim(),
                        poiCategory = PoiCategory.Gas,
                        fuelPrices = prices.ifEmpty { null },
                        source = "Peco Online (Romania)"
                    )
                }
                .take(limit)
                .toList()
        }
    }

    private suspend fun getOrFetchStations(): List<PecoStation> = mutex.withLock {
        cachedStations?.let { return@withLock it }
        return try {
            val stations = pecoClient.fetchAllStations()
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
