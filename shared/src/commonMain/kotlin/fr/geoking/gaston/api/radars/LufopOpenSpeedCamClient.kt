package fr.geoking.gaston.api.radars

import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.shared.location.haversineKm
import fr.geoking.gaston.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Client for the [Lufop API](https://api.lufop.net/) (speed cameras / danger zones).
 *
 * Geo query: `GET /api?key=…&format=json&q=lat,lon&m=…&nbr=…`
 * [m] is ≈ 1/10 km (e.g. `m=100` ≈ 10 km). Blank [apiKey] → empty results.
 */
class LufopOpenSpeedCamClient(
    private val client: HttpClient,
    private val apiKey: String = "",
    private val baseUrl: String = DEFAULT_API_URL,
    private val maxResults: Int = DEFAULT_MAX_RESULTS,
) {
    companion object {
        const val DEFAULT_API_URL = "https://api.lufop.net/api"
        /** Free-tier max results per call (Pro allows more). */
        const val DEFAULT_MAX_RESULTS = 200
        private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes
        private val VMA_IN_NAME = Regex("""(?:^|\D)(\d{2,3})\s*(?:km/?h)?\s*$""", RegexOption.IGNORE_CASE)
    }

    /** False when [apiKey] is blank — callers may fall back to OSM / official dumps. */
    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    private val mutex = Mutex()
    private var cachedRadars: List<LufopOpenSpeedCamRecord>? = null
    private var cacheLat: Double = Double.NaN
    private var cacheLon: Double = Double.NaN
    private var cacheRadiusKm: Double = 0.0
    private var cacheTimestamp: Long = 0L

    suspend fun getRadarsNear(
        latitude: Double,
        longitude: Double,
        radiusKm: Double = 15.0
    ): List<Poi> {
        return getRecordsNear(latitude, longitude, radiusKm).map { it.toPoi() }
    }

    suspend fun getRecordsNear(
        latitude: Double,
        longitude: Double,
        radiusKm: Double = 15.0
    ): List<LufopOpenSpeedCamRecord> {
        if (apiKey.isBlank()) return emptyList()

        val allRadars = ensureCachedRadars(latitude, longitude, radiusKm)
        return allRadars.filter { radar ->
            haversineKm(latitude, longitude, radar.latitude, radar.longitude) <= radiusKm
        }
    }

    suspend fun clearCache() {
        mutex.withLock {
            cachedRadars = null
            cacheLat = Double.NaN
            cacheLon = Double.NaN
            cacheRadiusKm = 0.0
            cacheTimestamp = 0L
        }
    }

    private suspend fun ensureCachedRadars(
        latitude: Double,
        longitude: Double,
        radiusKm: Double,
    ): List<LufopOpenSpeedCamRecord> {
        val now = System.currentTimeMillis()
        mutex.withLock {
            val existing = cachedRadars
            val stillFresh = existing != null &&
                (now - cacheTimestamp) < CACHE_TTL_MS &&
                !cacheLat.isNaN() &&
                haversineKm(latitude, longitude, cacheLat, cacheLon) <= cacheRadiusKm * 0.4 &&
                radiusKm <= cacheRadiusKm * 1.05
            if (stillFresh) return existing
        }

        val downloaded = fetchNear(latitude, longitude, radiusKm)
        // Never cache empty payloads (HTML challenge, quota, transient miss) — that blocked
        // all later retries for [CACHE_TTL_MS] with zero network calls.
        if (downloaded.isNotEmpty()) {
            mutex.withLock {
                cachedRadars = downloaded
                cacheLat = latitude
                cacheLon = longitude
                cacheRadiusKm = radiusKm
                cacheTimestamp = now
            }
        }
        return downloaded
    }

    private suspend fun fetchNear(
        latitude: Double,
        longitude: Double,
        radiusKm: Double,
    ): List<LufopOpenSpeedCamRecord> {
        // Lufop `m` ≈ 1/10 km (docs: m=100 ≈ 10 km around q=…).
        val margin = (radiusKm * 10.0).toInt().coerceIn(1, 10_000)
        val response = client.get(baseUrl) {
            parameter("key", apiKey)
            parameter("format", "json")
            parameter("q", "$latitude,$longitude")
            parameter("m", margin)
            parameter("nbr", maxResults.coerceIn(1, 10_000))
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw NetworkException(response.status.value, "Lufop API fetch error: ${body.take(200)}")
        }
        return parseContent(body)
    }

    fun parseContent(text: String): List<LufopOpenSpeedCamRecord> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.startsWith("<")) return emptyList() // HTML / XML error pages
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return parseJson(trimmed)
        }
        return emptyList()
    }

    private fun parseJson(jsonText: String): List<LufopOpenSpeedCamRecord> {
        val records = mutableListOf<LufopOpenSpeedCamRecord>()
        try {
            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(jsonText)

            val array = when {
                root is kotlinx.serialization.json.JsonArray -> root
                root is kotlinx.serialization.json.JsonObject -> {
                    root["markers"]?.jsonArray
                        ?: root["points"]?.jsonArray
                        ?: root["elements"]?.jsonArray
                        ?: root["data"]?.jsonArray
                }
                else -> null
            } ?: return emptyList()

            for ((idx, elem) in array.withIndex()) {
                val obj = elem.jsonObject
                val lat = obj["lat"]?.jsonPrimitive?.doubleOrNull
                    ?: obj["latitude"]?.jsonPrimitive?.doubleOrNull
                    ?: continue
                val lon = obj["lng"]?.jsonPrimitive?.doubleOrNull
                    ?: obj["lon"]?.jsonPrimitive?.doubleOrNull
                    ?: obj["longitude"]?.jsonPrimitive?.doubleOrNull
                    ?: continue
                val name = obj["name"]?.jsonPrimitive?.content
                val type = obj["type"]?.jsonPrimitive?.content ?: "unknown"
                val id = obj["ID"]?.jsonPrimitive?.content
                    ?: obj["id"]?.jsonPrimitive?.content
                    ?: "lufop_$idx"
                val commune = obj["commune"]?.jsonPrimitive?.content
                val voie = obj["voie"]?.jsonPrimitive?.content
                val flash = obj["flash"]?.jsonPrimitive?.content
                val azimut = obj["azimut"]?.jsonPrimitive?.doubleOrNull
                    ?: obj["azimut"]?.jsonPrimitive?.content?.toDoubleOrNull()
                // Live API uses string "vitesse"; older samples use int vma/speed.
                val speed = obj["vma"]?.jsonPrimitive?.intOrNull
                    ?: obj["speed"]?.jsonPrimitive?.intOrNull
                    ?: obj["speed_limit"]?.jsonPrimitive?.intOrNull
                    ?: obj["vitesse"]?.jsonPrimitive?.intOrNull
                    ?: obj["vitesse"]?.jsonPrimitive?.content?.toIntOrNull()
                    ?: name?.let { extractVmaFromName(it) }

                records.add(
                    LufopOpenSpeedCamRecord(
                        id = id,
                        type = type,
                        vma = speed,
                        latitude = lat,
                        longitude = lon,
                        name = name,
                        commune = commune,
                        voie = voie,
                        azimut = azimut,
                        flash = flash,
                    )
                )
            }
        } catch (_: Exception) {
            // Ignore parse errors
        }
        return records
    }

    internal fun extractVmaFromName(name: String): Int? {
        val match = VMA_IN_NAME.find(name.trim()) ?: return null
        val value = match.groupValues[1].toIntOrNull() ?: return null
        return value.takeIf { it in 20..160 }
    }
}

data class LufopOpenSpeedCamRecord(
    val id: String,
    val type: String,
    val vma: Int?,
    val latitude: Double,
    val longitude: Double,
    val name: String? = null,
    val commune: String? = null,
    val voie: String? = null,
    /** Bearing degrees from Lufop `azimut` when present. */
    val azimut: Double? = null,
    /** Lufop `flash` (e.g. F/B/"Double sens"). */
    val flash: String? = null,
) {
    fun toPoi(): Poi {
        val speedLabel = if (vma != null && vma > 0) "$vma km/h" else null
        val title = when {
            !name.isNullOrBlank() -> name
            speedLabel != null -> "Zone $speedLabel"
            else -> "Zone de vigilance ($type)"
        }
        val address = listOfNotNull(voie, commune)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(", ")
            .ifBlank { "Lufop" }
        val bidirectional = flash?.contains("double", ignoreCase = true) == true
        val raw = buildMap {
            put("vma", vma?.toString() ?: "NA")
            put("type", type)
            put("id", id)
            put("aac_zone", "true")
            flash?.takeIf { it.isNotBlank() }?.let { put("flash", it) }
            if (bidirectional) {
                put("bidirectional", "true")
                put("direction", "both")
            } else {
                azimut?.let { put("monitored_bearing", it.toString()) }
            }
        }
        return Poi(
            id = "osc_radar_$id",
            name = title,
            address = address,
            latitude = latitude,
            longitude = longitude,
            brand = null,
            isElectric = false,
            poiCategory = PoiCategory.Radar,
            source = "LufopOpenSpeedCam",
            rawSourceData = raw
        )
    }
}
