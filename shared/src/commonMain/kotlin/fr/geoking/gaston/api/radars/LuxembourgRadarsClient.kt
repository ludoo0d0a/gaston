package fr.geoking.gaston.api.radars

import fr.geoking.gaston.aac.TextFileCache
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.shared.location.haversineKm
import fr.geoking.gaston.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Client for Luxembourg fixed speed cameras (PCH / data.public.lu GeoJSON).
 * Licence: Creative Commons Zero (CC0).
 *
 * Dataset: [PCH : Emplacement des radars fixes](https://data.public.lu/fr/datasets/pch-emplacement-des-radars-fixes/)
 * Resource: [https://data.geoportail.lu/radar](https://data.geoportail.lu/radar)
 */
class LuxembourgRadarsClient(
    private val client: HttpClient,
    private val geoJsonUrl: String = DEFAULT_GEOJSON_URL,
    private val diskCache: TextFileCache? = null,
) {
    companion object {
        const val DEFAULT_GEOJSON_URL = "https://data.geoportail.lu/radar"
        const val DISK_CACHE_KEY = "luxembourg_radars_geojson"
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    private val mutex = Mutex()
    private var cachedRadars: List<LuxembourgRadarRecord>? = null
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
    ): List<LuxembourgRadarRecord> {
        val allRadars = ensureCachedRadars()
        return allRadars.filter { radar ->
            haversineKm(latitude, longitude, radar.latitude, radar.longitude) <= radiusKm
        }
    }

    suspend fun clearCache() {
        mutex.withLock {
            cachedRadars = null
            cacheTimestamp = 0L
        }
        diskCache?.clear(DISK_CACHE_KEY)
    }

    private suspend fun ensureCachedRadars(): List<LuxembourgRadarRecord> {
        val now = System.currentTimeMillis()
        mutex.withLock {
            val existing = cachedRadars
            if (existing != null && (now - cacheTimestamp) < CACHE_TTL_MS) {
                return existing
            }
        }

        diskCache?.read(DISK_CACHE_KEY)?.let { cached ->
            if ((now - cached.storedAtEpochMs) < CACHE_TTL_MS) {
                val parsed = parseGeoJson(cached.body)
                mutex.withLock {
                    cachedRadars = parsed
                    cacheTimestamp = cached.storedAtEpochMs
                }
                return parsed
            }
        }

        val downloaded = fetchAndParseRadars()
        mutex.withLock {
            cachedRadars = downloaded
            cacheTimestamp = now
        }
        return downloaded
    }

    private suspend fun fetchAndParseRadars(): List<LuxembourgRadarRecord> {
        val response = client.get(geoJsonUrl)
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw NetworkException(response.status.value, "LuxembourgRadars GeoJSON fetch error: ${body.take(200)}")
        }
        diskCache?.write(DISK_CACHE_KEY, body, null)
        return parseGeoJson(body)
    }

    fun parseGeoJson(text: String): List<LuxembourgRadarRecord> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        val records = mutableListOf<LuxembourgRadarRecord>()
        try {
            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(trimmed).jsonObject
            val features = root["features"]?.jsonArray ?: return emptyList()
            for ((idx, elem) in features.withIndex()) {
                val feature = elem.jsonObject
                val geometry = feature["geometry"]?.jsonObject ?: continue
                val coords = geometry["coordinates"]?.jsonArray ?: continue
                // GeoJSON Point: [lon, lat]
                val lon = coords.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: continue
                val lat = coords.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: continue
                val props = feature["properties"]?.jsonObject
                val id = props?.get("ID")?.jsonPrimitive?.content
                    ?: props?.get("OBJECTID_1")?.jsonPrimitive?.content
                    ?: "lu_$idx"
                val tranche = props?.get("TRANCON")?.jsonPrimitive?.content
                val dir = props?.get("DIR")?.jsonPrimitive?.content
                val dirOpposite = props?.get("DIR_")?.jsonPrimitive?.content
                val year = props?.get("YEAR")?.jsonPrimitive?.content
                records.add(
                    LuxembourgRadarRecord(
                        id = id,
                        latitude = lat,
                        longitude = lon,
                        tranche = tranche,
                        direction = dir,
                        oppositeDirection = dirOpposite,
                        year = year,
                    )
                )
            }
        } catch (_: Exception) {
            // Ignore parse errors
        }
        return records
    }
}

data class LuxembourgRadarRecord(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val tranche: String? = null,
    val direction: String? = null,
    val oppositeDirection: String? = null,
    val year: String? = null,
) {
    fun toPoi(): Poi {
        val title = tranche?.takeIf { it.isNotBlank() } ?: "Zone de vigilance"
        val address = listOfNotNull(direction, oppositeDirection)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(" / ")
            .ifBlank { "Luxembourg" }
        return Poi(
            id = "lu_radar_$id",
            name = title,
            address = address,
            latitude = latitude,
            longitude = longitude,
            brand = null,
            isElectric = false,
            poiCategory = PoiCategory.Radar,
            source = "LuxembourgRadars",
            rawSourceData = buildMap {
                put("id", id)
                put("type", "FIXE")
                put("vma", "NA")
                put("aac_zone", "true")
                tranche?.let { put("tranche", it) }
                year?.let { put("year", it) }
            }
        )
    }

    /** Shared AAC record shape for [toDangerZone]. */
    fun toFranceRadarRecord(): FranceRadarRecord = FranceRadarRecord(
        id = "lu_$id",
        type = "FIXE",
        vma = null,
        latitude = latitude,
        longitude = longitude,
    )
}
