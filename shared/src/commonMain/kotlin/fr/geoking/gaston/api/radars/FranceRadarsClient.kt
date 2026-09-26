package fr.geoking.gaston.api.radars

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
 * Client for French speed cameras dataset from data.gouv.fr ("Liste des radars fixes en France").
 * Open Data licence: Licence Ouverte v2.0 (Etalab).
 */
class FranceRadarsClient(
    private val client: HttpClient,
    private val csvUrl: String = DEFAULT_CSV_URL
) {
    companion object {
        const val DEFAULT_CSV_URL =
            "https://static.data.gouv.fr/resources/liste-des-radars-fixes-en-france/20251230-134204/jeu-de-donnees-liste-des-radars-fixes-en-france-12-2025.csv"
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    private val mutex = Mutex()
    private var cachedRadars: List<FranceRadarRecord>? = null
    private var cacheTimestamp: Long = 0L

    suspend fun getRadarsNear(
        latitude: Double,
        longitude: Double,
        radiusKm: Double = 15.0
    ): List<Poi> {
        val allRadars = ensureCachedRadars()
        return allRadars.mapNotNull { radar ->
            val dist = haversineKm(latitude, longitude, radar.latitude, radar.longitude)
            if (dist <= radiusKm) {
                radar.toPoi()
            } else {
                null
            }
        }
    }

    suspend fun clearCache() {
        mutex.withLock {
            cachedRadars = null
            cacheTimestamp = 0L
        }
    }

    private suspend fun ensureCachedRadars(): List<FranceRadarRecord> {
        val now = System.currentTimeMillis()
        mutex.withLock {
            val existing = cachedRadars
            if (existing != null && (now - cacheTimestamp) < CACHE_TTL_MS) {
                return existing
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
        val response = client.get(csvUrl)
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "FranceRadars CSV fetch error: ${body.take(200)}")
        }
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
                // Try to resolve column indices from header
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
        val speedLabel = if (vma != null && vma > 0) "$vma km/h" else null
        val title = if (speedLabel != null) "Radar $speedLabel" else "Radar ($type)"
        return Poi(
            id = "fr_radar_$id",
            name = title,
            address = "Radar $type — France",
            latitude = latitude,
            longitude = longitude,
            brand = "Radar",
            isElectric = false,
            poiCategory = PoiCategory.Radar,
            source = "FranceRadars",
            rawSourceData = mapOf(
                "vma" to (vma?.toString() ?: "NA"),
                "type" to type,
                "id" to id
            )
        )
    }
}
