package fr.geoking.gaston.api.uk

import fr.geoking.gaston.poi.radiusKmFromMapViewport
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * UK interim road fuel open data scheme:
 * retailers publish a JSON file following the CMA/Fuel Finder schema.
 *
 * Source list (updated by GOV.UK): see "Access fuel price data".
 */
class UkCmaFuelProvider(
    private val client: HttpClient,
    private val radiusKm: Int = 10,
    private val limit: Int = 150,
    private val cacheMaxAgeMs: Long = 15 * 60_000L,
) : PoiProvider {

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var cachedStations: List<Poi> = emptyList()
    private var cachedAtMs: Long = 0L

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    override suspend fun getGasStations(latitude: Double, longitude: Double, viewport: MapViewport?): List<Poi> {
        val effectiveRadiusKm = viewport
            ?.let { radiusKmFromMapViewport(latitude, longitude, it).coerceIn(1, 50) }
            ?: radiusKm

        val stations = getOrFetchAllStations()
        if (stations.isEmpty()) return emptyList()

        return withContext(Dispatchers.Default) {
            stations.asSequence()
                .map { it to haversineKm(latitude, longitude, it.latitude, it.longitude) }
                .filter { (_, km) -> km <= effectiveRadiusKm }
                .sortedBy { it.second }
                .take(limit)
                .map { it.first }
                .toList()
        }
    }

    override suspend fun clearCache() {
        cachedStations = emptyList()
        cachedAtMs = 0L
    }

    private suspend fun getOrFetchAllStations(): List<Poi> {
        val now = currentTimeMs()
        if (cachedStations.isNotEmpty() && now - cachedAtMs < cacheMaxAgeMs) return cachedStations

        return mutex.withLock {
            val now2 = currentTimeMs()
            if (cachedStations.isNotEmpty() && now2 - cachedAtMs < cacheMaxAgeMs) return@withLock cachedStations

            val merged = fetchRetailerFeeds()
            cachedStations = merged
            cachedAtMs = currentTimeMs()
            merged
        }
    }

    private suspend fun fetchRetailerFeeds(): List<Poi> = coroutineScope {
        val results = UK_RETAILER_FEEDS.map { url ->
            async {
                try {
                    val body = client.get(url).bodyAsText()
                    parseFeedJson(body, sourceUrl = url)
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }.awaitAll().flatten()

        // Deduplicate by id, keep first (feeds should not overlap, but some brands can)
        results.distinctBy { it.id }
    }

    private fun parseFeedJson(raw: String, sourceUrl: String): List<Poi> {
        val root = try {
            json.parseToJsonElement(raw).jsonObject
        } catch (_: Exception) {
            return emptyList()
        }
        val lastUpdated = root["last_updated"].asStringOrNull()
        val stations = (root["stations"] as? JsonArray)?.jsonArray ?: return emptyList()
        return stations.mapNotNull { stationEl ->
            stationEl as? JsonObject ?: return@mapNotNull null
            val siteId = stationEl["site_id"].asStringOrNull() ?: return@mapNotNull null
            val brand = stationEl["brand"].asStringOrNull()
            val address = stationEl["address"].asStringOrNull() ?: ""
            val postcode = stationEl["postcode"].asStringOrNull()
            val loc = stationEl["location"]?.jsonObject ?: return@mapNotNull null
            val lat = loc["latitude"].asDoubleOrNull() ?: return@mapNotNull null
            val lon = loc["longitude"].asDoubleOrNull() ?: return@mapNotNull null
            val prices = stationEl["prices"]?.jsonObject

            val fuelPrices = prices?.let { mapUkPrices(it, lastUpdated) }?.ifEmpty { null }

            Poi(
                id = "ukcma:$siteId",
                name = brand?.ifBlank { null } ?: "Fuel station",
                address = buildString {
                    append(address)
                    if (!postcode.isNullOrBlank()) {
                        if (isNotEmpty()) append(", ")
                        append(postcode)
                    }
                    append(" UK")
                },
                latitude = lat,
                longitude = lon,
                brand = brand,
                poiCategory = PoiCategory.Gas,
                fuelPrices = fuelPrices,
                source = "UK Fuel Finder (CMA): ${hostOf(sourceUrl)}"
            )
        }
    }

    private fun mapUkPrices(prices: JsonObject, lastUpdated: String?): List<FuelPrice> {
        fun penceToGbp(value: JsonElement?): Double? = value.asDoubleOrNull()?.let { it / 100.0 }
        val out = mutableListOf<FuelPrice>()
        // CMA schema: E10, E5, B7, SDV in pence
        penceToGbp(prices["E10"])?.let { out.add(FuelPrice("SP95 E10", it, updatedAt = lastUpdated)) }
        penceToGbp(prices["E5"])?.let { out.add(FuelPrice("SP98", it, updatedAt = lastUpdated)) }
        penceToGbp(prices["B7"])?.let { out.add(FuelPrice("Diesel", it, updatedAt = lastUpdated)) }
        penceToGbp(prices["SDV"])?.let { out.add(FuelPrice("Diesel Premium", it, updatedAt = lastUpdated)) }
        return out
    }

    private fun hostOf(url: String): String =
        url.removePrefix("https://").removePrefix("http://").substringBefore("/")

    private fun currentTimeMs(): Long = System.currentTimeMillis()

    private fun JsonElement?.asStringOrNull(): String? =
        try {
            this?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }

    private fun JsonElement?.asDoubleOrNull(): Double? =
        try {
            this?.jsonPrimitive?.content?.toDoubleOrNull()
        } catch (_: Exception) {
            null
        }

    companion object {
        /**
         * Source list: `https://www.gov.uk/guidance/access-fuel-price-data` (updated 2026-01-06).
         * Note: Shell link is HTML; the feed JSON is not directly stable there, so we skip it.
         */
        private val UK_RETAILER_FEEDS = listOf(
            "https://fuelprices.asconagroup.co.uk/newfuel.json",
            "https://storelocator.asda.com/fuel_prices_data.json",
            "https://www.bp.com/en_gb/united-kingdom/home/fuelprices/fuel_prices_data.json",
            "https://fuelprices.esso.co.uk/latestdata.json",
            "https://jetlocal.co.uk/fuel_prices_data.json",
            "https://devapi.krlpos.com/integration/live_price/krl",
            "https://www.morrisons.com/fuel-prices/fuel.json",
            "https://moto-way.com/fuel-price/fuel_prices.json",
            "https://fuel.motorfuelgroup.com/fuel_prices_data.json",
            "https://www.rontec-servicestations.co.uk/fuel-prices/data/fuel_prices_data.json",
            "https://api.sainsburys.co.uk/v1/exports/latest/fuel_prices_data.json",
            "https://www.sgnretail.uk/files/data/SGN_daily_fuel_prices.json",
            "https://www.tesco.com/fuel_prices/fuel_prices_data.json",
        )
    }
}

