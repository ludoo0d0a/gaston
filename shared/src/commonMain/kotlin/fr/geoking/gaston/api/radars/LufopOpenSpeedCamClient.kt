package fr.geoking.gaston.api.radars

import fr.geoking.gaston.aac.TextFileCache
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.shared.location.haversineKm
import fr.geoking.gaston.shared.network.NetworkException
import fr.geoking.gaston.shared.network.RateLimitTracker
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
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
 *
 * Aggressive cache: prefetch ≥ [MIN_FETCH_RADIUS_KM], TTL 30 days (memory + optional disk),
 * and at most one network call every [minNetworkIntervalMs] (free tier ≈ 10 req/min).
 */
class LufopOpenSpeedCamClient(
    private val client: HttpClient,
    private val apiKey: String = "",
    private val baseUrl: String = DEFAULT_API_URL,
    private val maxResults: Int = DEFAULT_MAX_RESULTS,
    private val diskCache: TextFileCache? = null,
    private val minNetworkIntervalMs: Long = MIN_NETWORK_INTERVAL_MS,
) {
    companion object {
        const val DEFAULT_API_URL = "https://api.lufop.net/api"
        /** Free-tier max results per call (Pro allows more). */
        const val DEFAULT_MAX_RESULTS = 200
        const val DISK_CACHE_KEY = "lufop_openspeedcam"
        /** Always fetch at least this radius so pans stay inside the cached blob. */
        const val MIN_FETCH_RADIUS_KM = 100.0
        private const val CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
        /** Mid of 6–10 s — keeps free-tier under ~10 calls/minute. */
        const val MIN_NETWORK_INTERVAL_MS = 8_000L
        private const val RATE_LIMIT_HOST = "api.lufop.net"
        private val VMA_IN_NAME = Regex("""(?:^|\D)(\d{2,3})\s*(?:km/?h)?\s*$""", RegexOption.IGNORE_CASE)
        private val QUOTA_HINT = Regex(
            """limite|quota|appels?/minute|temporairement bloqu""",
            RegexOption.IGNORE_CASE,
        )
    }

    /** False when [apiKey] is blank — callers may fall back to OSM / official dumps. */
    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    private val mutex = Mutex()
    private var cachedRadars: List<LufopOpenSpeedCamRecord>? = null
    private var cacheLat: Double = Double.NaN
    private var cacheLon: Double = Double.NaN
    private var cacheRadiusKm: Double = 0.0
    private var cacheTimestamp: Long = 0L
    private var lastNetworkCallMs: Long = 0L

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
            lastNetworkCallMs = 0L
        }
        diskCache?.clear(DISK_CACHE_KEY)
    }

    private suspend fun ensureCachedRadars(
        latitude: Double,
        longitude: Double,
        radiusKm: Double,
    ): List<LufopOpenSpeedCamRecord> {
        val fetchRadiusKm = maxOf(radiusKm, MIN_FETCH_RADIUS_KM)
        val now = System.currentTimeMillis()

        mutex.withLock {
            memoryIfFresh(latitude, longitude, fetchRadiusKm, now)?.let { return it }
        }

        val fromDisk = loadDiskIfFresh(latitude, longitude, fetchRadiusKm, now)
        if (fromDisk != null) return fromDisk

        mutex.withLock {
            // Another coroutine may have filled memory while we read disk.
            memoryIfFresh(latitude, longitude, fetchRadiusKm, now)?.let { return it }

            val throttled = lastNetworkCallMs > 0L &&
                (now - lastNetworkCallMs) < minNetworkIntervalMs
            if (throttled) {
                cachedRadars?.let { return it }
                throw NetworkException(
                    429,
                    "Lufop throttle: wait ${minNetworkIntervalMs / 1000}s between network calls",
                )
            }
        }

        if (RateLimitTracker.isRateLimited(RATE_LIMIT_HOST)) {
            mutex.withLock { cachedRadars }?.let { return it }
            throw NetworkException(429, RateLimitTracker.cooldownMessage(RATE_LIMIT_HOST))
        }

        val downloaded = fetchNear(latitude, longitude, fetchRadiusKm)
        // Never cache empty payloads (HTML challenge, quota, transient miss) — that blocked
        // all later retries for [CACHE_TTL_MS] with zero network calls.
        if (downloaded.isNotEmpty()) {
            mutex.withLock {
                cachedRadars = downloaded
                cacheLat = latitude
                cacheLon = longitude
                cacheRadiusKm = fetchRadiusKm
                cacheTimestamp = now
            }
        }
        return downloaded
    }

    private fun memoryIfFresh(
        latitude: Double,
        longitude: Double,
        fetchRadiusKm: Double,
        now: Long,
    ): List<LufopOpenSpeedCamRecord>? {
        val existing = cachedRadars ?: return null
        if (!isGeoFresh(latitude, longitude, fetchRadiusKm, cacheLat, cacheLon, cacheRadiusKm) ||
            (now - cacheTimestamp) >= CACHE_TTL_MS
        ) {
            return null
        }
        return existing
    }

    private suspend fun loadDiskIfFresh(
        latitude: Double,
        longitude: Double,
        fetchRadiusKm: Double,
        now: Long,
    ): List<LufopOpenSpeedCamRecord>? {
        val cached = diskCache?.read(DISK_CACHE_KEY) ?: return null
        if ((now - cached.storedAtEpochMs) >= CACHE_TTL_MS) return null
        val meta = parseDiskVersion(cached.version) ?: return null
        if (!isGeoFresh(latitude, longitude, fetchRadiusKm, meta.lat, meta.lon, meta.radiusKm)) {
            return null
        }
        val parsed = parseContent(cached.body)
        if (parsed.isEmpty()) return null
        mutex.withLock {
            cachedRadars = parsed
            cacheLat = meta.lat
            cacheLon = meta.lon
            cacheRadiusKm = meta.radiusKm
            cacheTimestamp = cached.storedAtEpochMs
        }
        return parsed
    }

    private fun isGeoFresh(
        latitude: Double,
        longitude: Double,
        fetchRadiusKm: Double,
        cachedLat: Double,
        cachedLon: Double,
        cachedRadiusKm: Double,
    ): Boolean {
        if (cachedLat.isNaN() || cachedRadiusKm <= 0.0) return false
        return haversineKm(latitude, longitude, cachedLat, cachedLon) <= cachedRadiusKm * 0.4 &&
            fetchRadiusKm <= cachedRadiusKm * 1.05
    }

    private data class DiskMeta(val lat: Double, val lon: Double, val radiusKm: Double)

    private fun parseDiskVersion(version: String?): DiskMeta? {
        if (version.isNullOrBlank()) return null
        val parts = version.split(',')
        if (parts.size != 3) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lon = parts[1].toDoubleOrNull() ?: return null
        val radius = parts[2].toDoubleOrNull() ?: return null
        return DiskMeta(lat, lon, radius)
    }

    private fun diskVersion(lat: Double, lon: Double, radiusKm: Double): String =
        "$lat,$lon,$radiusKm"

    private suspend fun fetchNear(
        latitude: Double,
        longitude: Double,
        radiusKm: Double,
    ): List<LufopOpenSpeedCamRecord> {
        // Lufop `m` ≈ 1/10 km (docs: m=100 ≈ 10 km around q=…).
        val margin = (radiusKm * 10.0).toInt().coerceIn(1, 10_000)
        mutex.withLock {
            lastNetworkCallMs = System.currentTimeMillis()
        }
        val response = try {
            client.get(baseUrl) {
                header(HttpHeaders.UserAgent, "Gaston/1.0 (contact@geoking.fr)")
                parameter("key", apiKey)
                parameter("format", "json")
                parameter("q", "$latitude,$longitude")
                parameter("m", margin)
                parameter("nbr", maxResults.coerceIn(1, 10_000))
            }
        } catch (e: NetworkException) {
            if (e.httpCode == 429) {
                val sec = RateLimitTracker.parseCooldownSecondsFromText(e.message) ?: 60L
                RateLimitTracker.recordRateLimit(
                    RATE_LIMIT_HOST,
                    retryAfterHeader = sec.toString(),
                    responseBody = e.message,
                )
            }
            throw e
        }
        val body = response.bodyAsText()
        if (response.status.value == 429 || looksLikeQuotaBody(body)) {
            val sec = RateLimitTracker.parseCooldownSecondsFromText(body) ?: 60L
            RateLimitTracker.recordRateLimit(
                RATE_LIMIT_HOST,
                retryAfterHeader = sec.toString(),
                responseBody = body,
            )
            throw NetworkException(429, "Lufop rate limit (cooldown $sec s remaining)")
        }
        if (response.status.value !in 200..299) {
            throw NetworkException(response.status.value, "Lufop API fetch error: ${body.take(200)}")
        }
        val records = parseContent(body)
        if (records.isNotEmpty()) {
            diskCache?.write(DISK_CACHE_KEY, body, diskVersion(latitude, longitude, radiusKm))
        }
        return records
    }

    private fun looksLikeQuotaBody(body: String): Boolean {
        val trimmed = body.trim()
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            // JSON payloads are never the French HTML/text quota page.
            return false
        }
        return QUOTA_HINT.containsMatchIn(trimmed)
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
                // Keep both keys: DangerZoneTriangle / RadarOsmEnricher use direction_bidirectional.
                put("bidirectional", "true")
                put(fr.geoking.gaston.aac.DangerZoneTriangle.RAW_BIDIRECTIONAL, "true")
                put(fr.geoking.gaston.aac.DangerZoneTriangle.RAW_DIRECTION, "both")
            } else {
                azimut?.let {
                    put(fr.geoking.gaston.aac.DangerZoneTriangle.RAW_MONITORED_BEARING, it.toString())
                }
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
