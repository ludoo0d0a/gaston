package fr.geoking.julius.api.nordic

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

class DrivstoffAppenProvider(
    client: HttpClient,
    private val radiusKm: Int = 20,
    private val limit: Int = 50
) : PoiProvider {

    private val drivstoffClient = DrivstoffAppenClient(client)
    private val mutex = Mutex()
    private val cachedStations = mutableMapOf<Int, List<DrivstoffStation>>()

    private val fuelTypeMap = mapOf(
        1 to "Gazole",
        2 to "SP95",
        3 to "SP98",
        4 to "Gazole", // Frigårdsdiesel
        7 to "HVO",
        8 to "SP95", // 92 Oktan (DK)
        9 to "E10", // E85
        47 to "SP95 Premium" // 100 Oktan (DK)
    )

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val countryId = when {
            // Norway
            latitude in 57.9..71.2 && longitude in 4.5..31.2 -> 1
            // Sweden
            latitude in 55.3..69.1 && longitude in 11.0..24.2 -> 2
            // Denmark
            latitude in 54.5..57.8 && longitude in 8.0..15.2 -> 3
            // Finland
            latitude in 59.7..70.1 && longitude in 20.5..31.6 -> 4
            else -> return emptyList()
        }

        val stations = getOrFetchStations(countryId)
        if (stations.isEmpty()) return emptyList()

        return withContext(Dispatchers.Default) {
            stations.asSequence()
                .filter { s ->
                    s.deleted == 0 && s.stationTypeId == 1 &&
                    haversineKm(latitude, longitude, s.coordinates.latitude, s.coordinates.longitude) <= radiusKm
                }
                .map { s ->
                    val prices = s.prices.mapNotNull { p ->
                        if (p.deleted == 0 && p.price > 0) {
                            fuelTypeMap[p.fuelTypeId]?.let { name ->
                                FuelPrice(fuelName = name, price = p.price)
                            }
                        } else null
                    }

                    Poi(
                        id = "drivstoff:${s.id}",
                        name = s.name.trim(),
                        address = s.location.trim(),
                        latitude = s.coordinates.latitude,
                        longitude = s.coordinates.longitude,
                        brand = s.brand.name.trim(),
                        poiCategory = PoiCategory.Gas,
                        fuelPrices = prices.ifEmpty { null },
                        source = "DrivstoffAppen"
                    )
                }
                .filter { it.fuelPrices != null }
                .take(limit)
                .toList()
        }
    }

    private suspend fun getOrFetchStations(countryId: Int): List<DrivstoffStation> = mutex.withLock {
        cachedStations[countryId]?.let { return@withLock it }
        return try {
            val stations = drivstoffClient.fetchStations(countryId)
            cachedStations[countryId] = stations
            stations
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun clearCache() {
        cachedStations.clear()
    }
}
