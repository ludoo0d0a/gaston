package fr.geoking.gaston.api.radars

import fr.geoking.gaston.aac.FranceRadarsCsvResolver
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

/**
 * Client for French fixed speed-control dataset from data.gouv.fr.
 * Open Data licence: Licence Ouverte v2.0 (Etalab).
 *
 * Resolves the latest CSV via data.gouv API when possible; falls back to [DEFAULT_CSV_URL].
 * Memory cache + optional [TextFileCache] disk TTL (24 h).
 */
class FranceRadarsClient(
    private val client: HttpClient,
    private val csvUrl: String = DEFAULT_CSV_URL,
    private val diskCache: TextFileCache? = null,
    private val csvResolver: FranceRadarsCsvResolver? = null,
) {
    companion object {
        const val DEFAULT_CSV_URL =
            "https://static.data.gouv.fr/resources/liste-des-radars-fixes-en-france/20251230-134204/jeu-de-donnees-liste-des-radars-fixes-en-france-12-2025.csv"
        const val DISK_CACHE_KEY = "france_radars_csv"
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    private val mutex = Mutex()
    private var cachedRadars: List<FranceRadarRecord>? = null
    private var cacheTimestamp: Long = 0L
    private var resolvedUrl: String? = null
    private var resolvedVersion: String? = null

    suspend fun getRadarsNear(
        latitude: Double,
        longitude: Double,
        radiusKm: Double = 15.0
    ): List<Poi> {
        return getRecordsNear(latitude, longitude, radiusKm).map { it.toPoi() }
    }

    /** Raw records for AAC zone conversion (not for alert pin UX). */
    suspend fun getRecordsNear(
        latitude: Double,
        longitude: Double,
        radiusKm: Double = 15.0
    ): List<FranceRadarRecord> {
        val allRadars = ensureCachedRadars()
        return allRadars.filter { radar ->
            haversineKm(latitude, longitude, radar.latitude, radar.longitude) <= radiusKm
        }
    }

    suspend fun clearCache() {
        mutex.withLock {
            cachedRadars = null
            cacheTimestamp = 0L
            resolvedUrl = null
            resolvedVersion = null
        }
        diskCache?.clear(DISK_CACHE_KEY)
    }

    private suspend fun ensureCachedRadars(): List<FranceRadarRecord> {
        val now = System.currentTimeMillis()
        mutex.withLock {
            val existing = cachedRadars
            if (existing != null && (now - cacheTimestamp) < CACHE_TTL_MS) {
                return existing
            }
        }

        // Disk cache
        diskCache?.read(DISK_CACHE_KEY)?.let { cached ->
            if ((now - cached.storedAtEpochMs) < CACHE_TTL_MS) {
                val parsed = parseCsv(cached.body)
                mutex.withLock {
                    cachedRadars = parsed
                    cacheTimestamp = cached.storedAtEpochMs
                    resolvedVersion = cached.version
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

    private suspend fun fetchAndParseRadars(): List<FranceRadarRecord> {
        val resolved = csvResolver?.resolveLatestCsvUrl()
        val url = resolved?.url ?: csvUrl
        resolvedUrl = url
        resolvedVersion = resolved?.resourceId ?: resolved?.lastModified

        val response = client.get(url)
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw NetworkException(response.status.value, "FranceRadars CSV fetch error: ${body.take(200)}")
        }
        diskCache?.write(DISK_CACHE_KEY, body, resolvedVersion)
        return parseCsv(body)
    }

    fun parseCsv(csvText: String): List<FranceRadarRecord> {
        val lines = csvText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val records = mutableListOf<FranceRadarRecord>()
        var headerParsed = false

        var idIdx = 0
        var typeIdx = 1
        var vmaIdx = 3
        var latIdx = 4
        var lonIdx = 5

        for (line in lines) {
            val parts = line.split(";").map { it.trim() }
            if (!headerParsed) {
                val headerLower = parts.map { it.lowercase() }
                for ((idx, col) in headerLower.withIndex()) {
                    when {
                        col.contains("numéro") || col.contains("numero") || col.contains("id") -> idIdx = idx
                        col.contains("type") -> typeIdx = idx
                        col.contains("vma") || col.contains("vitesse") -> vmaIdx = idx
                        col.contains("lat") -> latIdx = idx
                        col.contains("long") || col.contains("lon") -> lonIdx = idx
                    }
                }
                headerParsed = true
                continue
            }

            if (parts.size <= maxOf(latIdx, lonIdx)) continue

            val id = parts.getOrNull(idIdx)?.ifBlank { null } ?: continue
            val type = parts.getOrNull(typeIdx)?.ifBlank { null } ?: "FIXE"
            val vmaRaw = parts.getOrNull(vmaIdx)?.ifBlank { null } ?: "NA"
            val latStr = parts.getOrNull(latIdx)?.replace("+", "")?.replace(",", ".")
            val lonStr = parts.getOrNull(lonIdx)?.replace("+", "")?.replace(",", ".")

            val lat = latStr?.toDoubleOrNull() ?: continue
            val lon = lonStr?.toDoubleOrNull() ?: continue
            val vma = vmaRaw.toIntOrNull()

            records.add(
                FranceRadarRecord(
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

data class FranceRadarRecord(
    val id: String,
    val type: String,
    val vma: Int?,
    val latitude: Double,
    val longitude: Double
) {
    fun toPoi(): Poi {
        // Neutral POI label — alert UX must use DangerZone / AAC copy, not "Radar X km/h".
        val speedLabel = if (vma != null && vma > 0) "$vma km/h" else null
        val title = if (speedLabel != null) "Zone $speedLabel" else "Zone de vigilance ($type)"
        return Poi(
            id = "fr_radar_$id",
            name = title,
            address = "France",
            latitude = latitude,
            longitude = longitude,
            brand = null,
            isElectric = false,
            poiCategory = PoiCategory.Radar,
            source = "FranceRadars",
            rawSourceData = mapOf(
                "vma" to (vma?.toString() ?: "NA"),
                "type" to type,
                "id" to id,
                "aac_zone" to "true"
            )
        )
    }
}
