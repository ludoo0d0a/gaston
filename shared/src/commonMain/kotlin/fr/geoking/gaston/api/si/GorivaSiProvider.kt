package fr.geoking.gaston.api.si

import fr.geoking.gaston.api.routex.radiusKmFromMapViewport
import fr.geoking.gaston.poi.FuelPrice
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.shared.location.haversineKm
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Slovenia goriva.si REST API.
 *
 * Base: https://goriva.si/api/v1/search/
 * Query: ?lat=..&lng=..&radius=..&page=..
 */
class GorivaSiProvider(
    private val client: HttpClient,
    private val radiusKm: Int = 10,
    private val limit: Int = 150,
    private val cacheMaxAgeMs: Long = 10 * 60_000L,
) : PoiProvider {

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var cachedAllStations: List<GorivaStation> = emptyList()
    private var cachedAtMs: Long = 0L

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    override suspend fun getGasStations(latitude: Double, longitude: Double, viewport: MapViewport?): List<Poi> {
        val effectiveRadiusKm = viewport
            ?.let { radiusKmFromMapViewport(latitude, longitude, it.zoom, it.mapWidthPx, it.mapHeightPx).coerceIn(1, 200) }
            ?: radiusKm

        // goriva.si has ~551 stations total; easiest is to cache the full dataset using a large radius query.
        val all = getOrFetchAllStations()
        if (all.isEmpty()) return emptyList()

        return withContext(Dispatchers.Default) {
            all.asSequence()
                .map { it to haversineKm(latitude, longitude, it.lat, it.lng) }
                .filter { (_, km) -> km <= effectiveRadiusKm }
                .sortedBy { it.second }
                .take(limit)
                .map { (s, _) -> s.toPoi() }
                .toList()
        }
    }

    override suspend fun clearCache() {
        cachedAllStations = emptyList()
        cachedAtMs = 0L
    }

    private suspend fun getOrFetchAllStations(): List<GorivaStation> {
        val now = System.currentTimeMillis()
        if (cachedAllStations.isNotEmpty() && now - cachedAtMs < cacheMaxAgeMs) return cachedAllStations

        return mutex.withLock {
            val now2 = System.currentTimeMillis()
            if (cachedAllStations.isNotEmpty() && now2 - cachedAtMs < cacheMaxAgeMs) return@withLock cachedAllStations
            val fetched = fetchAllStations()
            cachedAllStations = fetched
            cachedAtMs = System.currentTimeMillis()
            fetched
        }
    }

    private suspend fun fetchAllStations(): List<GorivaStation> {
        val base = "https://goriva.si/api/v1/search/?lat=46.0569&lng=14.5058&radius=200"
        val out = mutableListOf<GorivaStation>()
        var nextUrl: String? = base
        var guard = 0
        while (nextUrl != null && guard < 50) {
            guard++
            val body = try { client.get(nextUrl).bodyAsText() } catch (_: Exception) { break }
            val page = try { json.decodeFromString<GorivaPage>(body) } catch (_: Exception) { break }
            out.addAll(page.results.orEmpty())
            nextUrl = page.next
        }
        return out
    }

    private fun GorivaStation.toPoi(): Poi {
        val prices = mutableListOf<FuelPrice>()
        pricesSl?.dizel?.let { prices.add(FuelPrice("Diesel", it, updatedAt = null)) }
        pricesSl?.get95()?.let { prices.add(FuelPrice("SP95", it, updatedAt = null)) }
        pricesSl?.get98()?.let { prices.add(FuelPrice("SP98", it, updatedAt = null)) }
        pricesSl?.lpg?.let { prices.add(FuelPrice("LPG", it, updatedAt = null)) }
        pricesSl?.cng?.let { prices.add(FuelPrice("CNG", it, updatedAt = null)) }
        pricesSl?.lng?.let { prices.add(FuelPrice("LNG", it, updatedAt = null)) }
        pricesSl?.hvo?.let { prices.add(FuelPrice("HVO", it, updatedAt = null)) }

        return Poi(
            id = "goriva:${pk}",
            name = name.ifBlank { "Fuel station" },
            address = buildString {
                append(address)
                if (!zipCode.isNullOrBlank()) {
                    if (isNotEmpty()) append(", ")
                    append(zipCode)
                }
                append(" SI")
            },
            latitude = lat,
            longitude = lng,
            brand = name,
            poiCategory = PoiCategory.Gas,
            fuelPrices = prices.ifEmpty { null },
            source = "goriva.si"
        )
    }
}

@Serializable
private data class GorivaPage(
    val count: Int? = null,
    val next: String? = null,
    val previous: String? = null,
    val results: List<GorivaStation>? = null,
)

@Serializable
private data class GorivaStation(
    val pk: Int,
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    @SerialName("zip_code") val zipCode: String? = null,
    @SerialName("open_hours") val openHours: String? = null,
    @SerialName("prices") val pricesSl: GorivaPrices? = null,
)

@Serializable
private data class GorivaPrices(
    // keys are dynamic; model the important ones that exist in the API
    @SerialName("dizel") val dizel: Double? = null,
    @SerialName("avtoplin-lpg") val lpg: Double? = null,
    @SerialName("cng") val cng: Double? = null,
    @SerialName("lng") val lng: Double? = null,
    @SerialName("hvo") val hvo: Double? = null,
    // numeric keys "95" and "98" are valid JSON keys, but Kotlin properties can't start with digits.
    @SerialName("95") val n95: Double? = null,
    @SerialName("98") val n98: Double? = null,
) {
    fun get95(): Double? = n95
    fun get98(): Double? = n98
}

