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
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Client for Lufop / OpenSpeedCam speed camera dataset.
 * Memory cache + optional [TextFileCache] disk TTL (24 h).
 */
class LufopOpenSpeedCamClient(
    private val client: HttpClient,
    private val exportUrl: String = DEFAULT_EXPORT_URL,
    private val diskCache: TextFileCache? = null,
) {
    companion object {
        const val DEFAULT_EXPORT_URL = "https://openspeedcam.net/export"
        const val DISK_CACHE_KEY = "lufop_openspeedcam_data"
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    private val mutex = Mutex()
    private var cachedRadars: List<LufopOpenSpeedCamRecord>? = null
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

    private suspend fun ensureCachedRadars(): List<LufopOpenSpeedCamRecord> {
        val now = System.currentTimeMillis()
        mutex.withLock {
            val existing = cachedRadars
            if (existing != null && (now - cacheTimestamp) < CACHE_TTL_MS) {
                return existing
            }
        }

        diskCache?.read(DISK_CACHE_KEY)?.let { cached ->
            if ((now - cached.storedAtEpochMs) < CACHE_TTL_MS) {
                val parsed = parseContent(cached.body)
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

    private suspend fun fetchAndParseRadars(): List<LufopOpenSpeedCamRecord> {
        val response = client.get(exportUrl)
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw NetworkException(response.status.value, "Lufop / OpenSpeedCam fetch error: ${body.take(200)}")
        }
        diskCache?.write(DISK_CACHE_KEY, body, null)
        return parseContent(body)
    }

    fun parseContent(text: String): List<LufopOpenSpeedCamRecord> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return parseJson(trimmed)
        }
        return parseCsv(trimmed)
    }

    private fun parseJson(jsonText: String): List<LufopOpenSpeedCamRecord> {
        val records = mutableListOf<LufopOpenSpeedCamRecord>()
        try {
            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(jsonText)

            val array = when {
                root is kotlinx.serialization.json.JsonArray -> root
                root is kotlinx.serialization.json.JsonObject -> {
                    root["points"]?.jsonArray
                        ?: root["elements"]?.jsonArray
                        ?: root["data"]?.jsonArray
                }
                else -> null
            } ?: return emptyList()

            for ((idx, elem) in array.withIndex()) {
                val obj = elem.jsonObject
                val lat = obj["latitude"]?.jsonPrimitive?.doubleOrNull
                    ?: obj["lat"]?.jsonPrimitive?.doubleOrNull
                    ?: continue
                val lon = obj["longitude"]?.jsonPrimitive?.doubleOrNull
                    ?: obj["lon"]?.jsonPrimitive?.doubleOrNull
                    ?: obj["lng"]?.jsonPrimitive?.doubleOrNull
                    ?: continue
                val speed = obj["vma"]?.jsonPrimitive?.intOrNull
                    ?: obj["speed"]?.jsonPrimitive?.intOrNull
                    ?: obj["speed_limit"]?.jsonPrimitive?.intOrNull
                val type = obj["type"]?.jsonPrimitive?.content ?: "FIXE"
                val id = obj["id"]?.jsonPrimitive?.content ?: "osc_$idx"

                records.add(
                    LufopOpenSpeedCamRecord(
                        id = id,
                        type = type,
                        vma = speed,
                        latitude = lat,
                        longitude = lon
                    )
                )
            }
        } catch (_: Exception) {
            // Ignore parse errors
        }
        return records
    }

    private fun parseCsv(csvText: String): List<LufopOpenSpeedCamRecord> {
        val lines = csvText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val records = mutableListOf<LufopOpenSpeedCamRecord>()
        var headerParsed = false

        var idIdx = -1
        var typeIdx = -1
        var vmaIdx = -1
        var latIdx = -1
        var lonIdx = -1

        val delimiter = if (lines.first().contains(";")) ";" else ","

        for ((lineIdx, line) in lines.withIndex()) {
            val parts = line.split(delimiter).map { it.trim() }
            if (!headerParsed) {
                val headerLower = parts.map { it.lowercase() }
                if (headerLower.any { it.contains("lat") || it.contains("lon") || it.contains("type") || it.contains("speed") }) {
                    for ((idx, col) in headerLower.withIndex()) {
                        when {
                            col.contains("numéro") || col.contains("numero") || col == "id" -> idIdx = idx
                            col.contains("type") -> typeIdx = idx
                            col.contains("vma") || col.contains("vitesse") || col.contains("speed") -> vmaIdx = idx
                            col.contains("lat") -> latIdx = idx
                            col.contains("long") || col.contains("lon") || col.contains("lng") -> lonIdx = idx
                        }
                    }
                    headerParsed = true
                    continue
                } else {
                    headerParsed = true
                }
            }

            val effectiveLatIdx = if (latIdx >= 0) latIdx else (if (parts.size >= 2 && parts[0].toDoubleOrNull() != null && parts[1].toDoubleOrNull() != null) {
                val p0 = parts[0].toDoubleOrNull()!!
                val p1 = parts[1].toDoubleOrNull()!!
                if (p0 in -90.0..90.0 && p1 in -180.0..180.0) {
                    if (p0 > 20.0) 0 else 1
                } else 0
            } else 0)
            val effectiveLonIdx = if (lonIdx >= 0) lonIdx else (if (effectiveLatIdx == 0) 1 else 0)

            if (parts.size <= maxOf(effectiveLatIdx, effectiveLonIdx)) continue

            val latStr = parts.getOrNull(effectiveLatIdx)?.replace("+", "")?.replace(",", ".")
            val lonStr = parts.getOrNull(effectiveLonIdx)?.replace("+", "")?.replace(",", ".")

            val lat = latStr?.toDoubleOrNull() ?: continue
            val lon = lonStr?.toDoubleOrNull() ?: continue

            val id = if (idIdx >= 0) parts.getOrNull(idIdx)?.ifBlank { null } ?: "osc_$lineIdx" else "osc_$lineIdx"
            val type = if (typeIdx >= 0) parts.getOrNull(typeIdx)?.ifBlank { null } ?: "FIXE" else "FIXE"
            val vmaRaw = if (vmaIdx >= 0) parts.getOrNull(vmaIdx) else null
            val vma = vmaRaw?.toIntOrNull()

            records.add(
                LufopOpenSpeedCamRecord(
                    id = id,
                    type = type,
                    vma = vma,
                    latitude = lat,
                    longitude = lon
                )
            )
        }
        return records
    }
}

data class LufopOpenSpeedCamRecord(
    val id: String,
    val type: String,
    val vma: Int?,
    val latitude: Double,
    val longitude: Double
) {
    fun toPoi(): Poi {
        val speedLabel = if (vma != null && vma > 0) "$vma km/h" else null
        val title = if (speedLabel != null) "Zone $speedLabel" else "Zone de vigilance ($type)"
        return Poi(
            id = "osc_radar_$id",
            name = title,
            address = "OpenSpeedCam / Lufop",
            latitude = latitude,
            longitude = longitude,
            brand = null,
            isElectric = false,
            poiCategory = PoiCategory.Radar,
            source = "LufopOpenSpeedCam",
            rawSourceData = mapOf(
                "vma" to (vma?.toString() ?: "NA"),
                "type" to type,
                "id" to id,
                "aac_zone" to "true"
            )
        )
    }
}
