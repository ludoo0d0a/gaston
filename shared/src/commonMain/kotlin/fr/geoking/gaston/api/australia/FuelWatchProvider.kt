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

/** Western Australia government fuel prices (FuelWatch). */
class FuelWatchProvider(
    client: HttpClient,
    private val radiusKm: Int = 30,
    private val limit: Int = 80,
) : PoiProvider {

    private val fuelWatchClient = FuelWatchClient(client)
    private val mutex = Mutex()
    private var cachedPois: List<Poi>? = null

    private val fuelNameMap = mapOf(
        "ULP" to "SP95",
        "PULP" to "SP98",
        "DSL" to "Gazole",
        "BDL" to "Gazole Premium",
        "LPG" to "GPL",
    )

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?,
    ): List<Poi> {
        if (latitude < -35.5 || latitude > -13.5 || longitude < 112.9 || longitude > 129.0) {
            return emptyList()
        }
        val all = getOrFetchPois()
        return withContext(Dispatchers.Default) {
            all.asSequence()
                .filter { p -> haversineKm(latitude, longitude, p.latitude, p.longitude) <= radiusKm }
                .take(limit)
                .toList()
        }
    }

    private suspend fun getOrFetchPois(): List<Poi> = mutex.withLock {
        cachedPois?.let { return@withLock it }
        return try {
            val pois = buildPois()
            cachedPois = pois
            pois
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun buildPois(): List<Poi> {
        val pricesByStation = linkedMapOf<Int, MutableList<FuelPrice>>()
        val metaByStation = linkedMapOf<Int, FuelWatchStation>()
        val products = fuelWatchClient.fetchProductTypes()
        for (product in products) {
            for (station in fuelWatchClient.fetchStationsForProduct(product)) {
                val price = station.product?.priceToday?.takeIf { it > 0 } ?: continue
                val fuelName = fuelNameMap[station.productFuelType] ?: station.productFuelType
                val list = pricesByStation.getOrPut(station.id) { mutableListOf() }
                if (list.none { it.fuelName == fuelName }) {
                    list.add(FuelPrice(fuelName, price / 100.0))
                }
                metaByStation.putIfAbsent(station.id, station)
            }
        }
        return metaByStation.map { (id, station) ->
            val addr = station.address
            Poi(
                id = "fuelwatch:$id",
                name = station.siteName.trim(),
                address = listOfNotNull(addr.line1, addr.postCode).joinToString(" "),
                latitude = addr.latitude,
                longitude = addr.longitude,
                brand = station.brandName,
                poiCategory = PoiCategory.Gas,
                fuelPrices = pricesByStation[id].orEmpty().ifEmpty { null },
                source = "FuelWatch (WA Australia)",
            )
        }
    }

    override suspend fun clearCache() {
        cachedPois = null
    }
}
