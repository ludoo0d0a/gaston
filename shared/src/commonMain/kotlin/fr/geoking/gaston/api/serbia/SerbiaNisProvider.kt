package fr.geoking.gaston.api.serbia

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

class SerbiaNisProvider(
    client: HttpClient,
    private val radiusKm: Int = 20,
    private val limit: Int = 50
) : PoiProvider {

    private val nisClient = SerbiaNisClient(client)
    private val mutex = Mutex()
    private var cachedStations: List<NISStation>? = null
    private var cachedBrandPrices: Map<String, List<BrandPrice>>? = null

    private val cenaFuelPages = mapOf(
        "SP95" to "/",
        "SP98" to "/bmb-100",
        "SP95 Premium" to "/bmb-premijum",
        "Gazole" to "/evro-dizel",
        "Gazole Premium" to "/evro-dizel-premijum",
        "GPL" to "/tng"
    )

    private val nisFuelMap = mapOf(
        "EVRO PREMIJUM BMB-95" to "SP95",
        "EBMB100 GDRIVE100" to "SP98",
        "G-DRIVE DIZEL" to "Gazole Premium",
        "EVRO DIZEL" to "Gazole",
        "AUTOGAS TNG" to "GPL",
        "CNG" to "CNG"
    )

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val stations = getOrFetchStations()
        val brandPrices = getOrFetchBrandPrices()
        if (stations.isEmpty()) return emptyList()

        return withContext(Dispatchers.Default) {
            stations.asSequence()
                .filter { s ->
                    haversineKm(latitude, longitude, s.Latitude, s.Longitude) <= radiusKm
                }
                .map { s ->
                    val brand = s.Brend?.trim() ?: "NIS Petrol"
                    val stationFuelTypes = s.Goriva?.mapNotNull { nisFuelMap[it.NazivRobe.trim()] }?.toSet() ?: emptySet()

                    val prices = stationFuelTypes.mapNotNull { fuelName ->
                        val pricesForType = brandPrices[fuelName] ?: return@mapNotNull null
                        val price = findBrandPrice(pricesForType, brand)
                        if (price != null && price > 0) {
                            FuelPrice(fuelName, price)
                        } else null
                    }

                    Poi(
                        id = "nis:${s.Pj}",
                        name = s.Naziv?.trim() ?: "NIS Station",
                        address = s.Adresa?.trim() ?: "",
                        latitude = s.Latitude,
                        longitude = s.Longitude,
                        brand = brand,
                        poiCategory = PoiCategory.Gas,
                        fuelPrices = prices.ifEmpty { null },
                        source = "NIS / Cenagoriva (Serbia)"
                    )
                }
                .take(limit)
                .toList()
        }
    }

    private suspend fun getOrFetchStations(): List<NISStation> = mutex.withLock {
        cachedStations?.let { return@withLock it }
        return try {
            val stations = nisClient.fetchNISStations()
            cachedStations = stations
            stations
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun getOrFetchBrandPrices(): Map<String, List<BrandPrice>> = mutex.withLock {
        cachedBrandPrices?.let { return@withLock it }
        val result = mutableMapOf<String, List<BrandPrice>>()
        cenaFuelPages.forEach { (fuelName, path) ->
            try {
                result[fuelName] = nisClient.fetchBrandPrices(path)
            } catch (e: Exception) { }
        }
        cachedBrandPrices = result
        return@withLock result
    }

    private fun findBrandPrice(brandPrices: List<BrandPrice>, stationBrand: String): Double? {
        val normalized = stationBrand.lowercase()
        // Direct match
        brandPrices.find { it.brand == normalized }?.let { return it.price }
        // NIS / Gazprom fallback
        if (normalized.contains("nis") || normalized.contains("gazprom")) {
            brandPrices.find { it.brand == "nis" }?.let { return it.price }
        }
        return null
    }

    override fun clearCache() {
        cachedStations = null
        cachedBrandPrices = null
    }
}
