package fr.geoking.gaston.api.croatia

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

class CroatiaMzoeProvider(
    client: HttpClient,
    private val radiusKm: Int = 20,
    private val limit: Int = 50
) : PoiProvider {

    private val mzoeClient = CroatiaMzoeClient(client)
    private val mutex = Mutex()
    private var cachedData: MZOEData? = null

    private val vrstaFuelMap = mapOf(
        2 to "SP95",
        1 to "SP95 Premium",
        5 to "SP98",
        6 to "SP98",
        8 to "Gazole",
        7 to "Gazole Premium",
        9 to "GPL"
    )

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val data = getOrFetchData() ?: return emptyList()

        val gorivoToVrsta = data.gorivos.mapNotNull { g ->
            g.vrsta_goriva_id?.let { g.id to it }
        }.toMap()

        val brandMap = data.obvezniks.associate { it.id to it.naziv }

        return withContext(Dispatchers.Default) {
            data.postajas.asSequence()
                .filter { s ->
                    // API bug: lat contains longitude, long contains latitude
                    haversineKm(latitude, longitude, s.long, s.lat) <= radiusKm
                }
                .map { s ->
                    val brand = brandMap[s.obveznik_id]?.replace(Regex("\\s*(d\\.d\\.|d\\.o\\.o\\.|d\\.o\\.o|j\\.d\\.o\\.o\\.)\\s*$", RegexOption.IGNORE_CASE), "")?.trim()

                    val prices = s.cjenici.mapNotNull { c ->
                        val vrstaId = gorivoToVrsta[c.gorivo_id] ?: return@mapNotNull null
                        val fuelName = vrstaFuelMap[vrstaId] ?: return@mapNotNull null
                        if (c.cijena in 0.3..4.0) {
                            FuelPrice(fuelName, c.cijena)
                        } else null
                    }

                    Poi(
                        id = "mzoe:${s.id}",
                        name = s.naziv?.trim().orEmpty().ifBlank { "Gas station" },
                        address = s.adresa?.trim().orEmpty(),
                        latitude = s.long,
                        longitude = s.lat,
                        brand = brand,
                        poiCategory = PoiCategory.Gas,
                        fuelPrices = prices.ifEmpty { null },
                        source = "MZOE (Croatia)"
                    )
                }
                .take(limit)
                .toList()
        }
    }

    private suspend fun getOrFetchData(): MZOEData? = mutex.withLock {
        cachedData?.let { return@withLock it }
        return try {
            val data = mzoeClient.fetchData()
            cachedData = data
            data
        } catch (e: Exception) {
            null
        }
    }

    override fun clearCache() {
        cachedData = null
    }
}
