package fr.geoking.gaston.api.dotnl

import fr.geoking.gaston.api.common.OcpiEvseAvailability
import fr.geoking.gaston.shared.location.haversineKm
import fr.geoking.gaston.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

/**
 * Netherlands DOT-NL (NDW) EVSE availability via the open OCPI 2.2.1 locations dump
 * ([opendata.ndw.nu](https://opendata.ndw.nu/charging_point_locations_ocpi.json.gz)).
 * No API key. Status is embedded per EVSE.
 *
 * Docs: https://docs.ndw.nu/en/data-uitwisseling/interface-beschrijvingen/dafne-api/dafne_api_consumer_pull/
 */
class DotNlAvailabilityClient(
    private val client: HttpClient,
    private val locationsUrl: String = DEFAULT_LOCATIONS_URL,
    private val cacheTtlMs: Long = 180_000L,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var cache: CachedLocations? = null

    /**
     * Returns PDC availability near [latitude]/[longitude] within [radiusKm], up to [limit].
     * Skips EVSEs with status REMOVED.
     */
    suspend fun getAvailability(
        latitude: Double,
        longitude: Double,
        radiusKm: Int = 15,
        limit: Int = 200,
    ): List<DotNlPdcRecord> {
        return filterAvailability(
            locations = getOrFetchLocations(),
            latitude = latitude,
            longitude = longitude,
            radiusKm = radiusKm,
            limit = limit,
        )
    }

    internal fun filterAvailability(
        locations: List<DotNlLocation>,
        latitude: Double,
        longitude: Double,
        radiusKm: Int,
        limit: Int,
    ): List<DotNlPdcRecord> {
        if (locations.isEmpty()) return emptyList()

        return locations.asSequence()
            .flatMap { loc ->
                val lat = loc.coordinates?.latitude?.toDoubleOrNull() ?: return@flatMap emptySequence()
                val lon = loc.coordinates?.longitude?.toDoubleOrNull() ?: return@flatMap emptySequence()
                val dist = haversineKm(latitude, longitude, lat, lon)
                if (dist > radiusKm) return@flatMap emptySequence()
                (loc.evses.orEmpty()).asSequence().mapNotNull { evse ->
                    val id = evse.evseId?.takeIf { it.isNotBlank() }
                        ?: evse.uid?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    if (OcpiEvseAvailability.isRemoved(evse.status)) return@mapNotNull null
                    DotNlPdcRecord(
                        id = id,
                        statusRaw = evse.status?.trim().orEmpty(),
                        latitude = lat,
                        longitude = lon,
                        stationId = loc.id,
                        address = listOfNotNull(loc.address, loc.city).joinToString(", ").ifBlank { null },
                        distanceKm = dist,
                    )
                }
            }
            .sortedBy { it.distanceKm }
            .take(limit)
            .toList()
    }

    internal fun parseLocationsJson(text: String): List<DotNlLocation> {
        if (text.isBlank()) return emptyList()
        return json.decodeFromString(text)
    }

    /** Gunzip when payload starts with gzip magic; otherwise treat as UTF-8 JSON. */
    internal fun decodeBody(bytes: ByteArray): String {
        if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            return GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
        return bytes.decodeToString()
    }

    private suspend fun getOrFetchLocations(): List<DotNlLocation> = mutex.withLock {
        val cached = cache
        val now = nowMs()
        if (cached != null && now - cached.atMs < cacheTtlMs) return@withLock cached.locations
        val response = client.get(locationsUrl)
        if (response.status.value !in 200..299) {
            val body = runCatching { response.bodyAsText() }.getOrElse { "<binary>" }
            throw NetworkException(response.status.value, "DOT-NL locations API error: $body")
        }
        val text = decodeBody(response.bodyAsBytes())
        val locations = parseLocationsJson(text)
        cache = CachedLocations(locations, now)
        locations
    }

    companion object {
        const val DEFAULT_LOCATIONS_URL =
            "https://opendata.ndw.nu/charging_point_locations_ocpi.json.gz"
    }

    private data class CachedLocations(val locations: List<DotNlLocation>, val atMs: Long)
}

@Serializable
data class DotNlLocation(
    val id: String? = null,
    val name: String? = null,
    val address: String? = null,
    val city: String? = null,
    val coordinates: DotNlCoordinates? = null,
    val evses: List<DotNlEvse>? = null,
)

@Serializable
data class DotNlCoordinates(
    val latitude: String? = null,
    val longitude: String? = null,
)

@Serializable
data class DotNlEvse(
    val uid: String? = null,
    @SerialName("evse_id") val evseId: String? = null,
    val status: String? = null,
)

data class DotNlPdcRecord(
    val id: String,
    val statusRaw: String,
    val latitude: Double,
    val longitude: Double,
    val stationId: String? = null,
    val address: String? = null,
    val distanceKm: Double = 0.0,
)
