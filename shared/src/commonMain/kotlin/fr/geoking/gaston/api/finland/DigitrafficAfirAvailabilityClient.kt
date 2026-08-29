package fr.geoking.gaston.api.finland

import fr.geoking.gaston.api.common.OcpiEvseAvailability
import fr.geoking.gaston.shared.location.haversineKm
import fr.geoking.gaston.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

/**
 * Finland Digitraffic AFIR EVSE availability via open snapshots
 * ([digitraffic.fi AFIR](https://www.digitraffic.fi/en/road-traffic/afir/)).
 * No API key. Locations (GeoJSON) + statuses (JSON) joined by EVSE id.
 *
 * Digitraffic requires `Accept-Encoding: gzip`. Prefer `/all` snapshot URLs
 * over paginated `?limit=ALL` redirects.
 */
class DigitrafficAfirAvailabilityClient(
    private val client: HttpClient,
    private val locationsUrl: String = DEFAULT_LOCATIONS_URL,
    private val statusesUrl: String = DEFAULT_STATUSES_URL,
    private val locationsCacheTtlMs: Long = 900_000L,
    private val statusesCacheTtlMs: Long = 60_000L,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var locationsCache: CachedLocations? = null
    private var statusesCache: CachedStatuses? = null

    /**
     * Returns PDC availability near [latitude]/[longitude] within [radiusKm], up to [limit].
     * Skips EVSEs with status REMOVED.
     */
    suspend fun getAvailability(
        latitude: Double,
        longitude: Double,
        radiusKm: Int = 15,
        limit: Int = 200,
    ): List<DigitrafficAfirPdcRecord> {
        val locations = getOrFetchLocations()
        val statusByEvseId = getOrFetchStatusMap()
        return filterAvailability(
            locations = locations,
            statusByEvseId = statusByEvseId,
            latitude = latitude,
            longitude = longitude,
            radiusKm = radiusKm,
            limit = limit,
        )
    }

    internal fun filterAvailability(
        locations: List<DigitrafficAfirLocation>,
        statusByEvseId: Map<String, String>,
        latitude: Double,
        longitude: Double,
        radiusKm: Int,
        limit: Int,
    ): List<DigitrafficAfirPdcRecord> {
        if (locations.isEmpty()) return emptyList()

        return locations.asSequence()
            .flatMap { loc ->
                val lat = loc.latitude ?: return@flatMap emptySequence()
                val lon = loc.longitude ?: return@flatMap emptySequence()
                val dist = haversineKm(latitude, longitude, lat, lon)
                if (dist > radiusKm) return@flatMap emptySequence()
                loc.evseIds.asSequence().mapNotNull { evseId ->
                    val id = evseId.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val statusRaw = statusByEvseId[id]?.trim().orEmpty()
                    if (OcpiEvseAvailability.isRemoved(statusRaw)) return@mapNotNull null
                    DigitrafficAfirPdcRecord(
                        id = id,
                        statusRaw = statusRaw.ifBlank { "UNKNOWN" },
                        latitude = lat,
                        longitude = lon,
                        stationId = loc.id,
                        address = loc.address,
                        distanceKm = dist,
                    )
                }
            }
            .sortedBy { it.distanceKm }
            .take(limit)
            .toList()
    }

    internal fun parseLocationsJson(text: String): List<DigitrafficAfirLocation> {
        if (text.isBlank()) return emptyList()
        val collection = json.decodeFromString<DigitrafficAfirLocationsResponse>(text)
        return collection.features.orEmpty().mapNotNull { feature ->
            val props = feature.properties ?: return@mapNotNull null
            val id = props.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val coords = feature.geometry?.coordinates ?: return@mapNotNull null
            if (coords.size < 2) return@mapNotNull null
            val lon = coords[0]
            val lat = coords[1]
            val address = listOfNotNull(props.address?.street, props.address?.city)
                .joinToString(", ")
                .ifBlank { null }
            DigitrafficAfirLocation(
                id = id,
                latitude = lat,
                longitude = lon,
                address = address,
                evseIds = props.evses.orEmpty().mapNotNull { it.id?.takeIf { id -> id.isNotBlank() } },
            )
        }
    }

    internal fun parseStatusesJson(text: String): Map<String, String> {
        if (text.isBlank()) return emptyMap()
        val response = json.decodeFromString<DigitrafficAfirStatusesResponse>(text)
        val map = HashMap<String, String>(response.statuses.orEmpty().size)
        for (item in response.statuses.orEmpty()) {
            val id = item.evseId?.takeIf { it.isNotBlank() } ?: continue
            map[id] = item.status?.trim().orEmpty()
        }
        return map
    }

    /** Gunzip when payload starts with gzip magic; otherwise treat as UTF-8 JSON. */
    internal fun decodeBody(bytes: ByteArray): String {
        if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            return GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
        return bytes.decodeToString()
    }

    private suspend fun getOrFetchLocations(): List<DigitrafficAfirLocation> = mutex.withLock {
        val cached = locationsCache
        val now = nowMs()
        if (cached != null && now - cached.atMs < locationsCacheTtlMs) return@withLock cached.locations
        val text = fetchGzipRequired(locationsUrl, "Digitraffic AFIR locations")
        val locations = parseLocationsJson(text)
        locationsCache = CachedLocations(locations, now)
        locations
    }

    private suspend fun getOrFetchStatusMap(): Map<String, String> = mutex.withLock {
        val cached = statusesCache
        val now = nowMs()
        if (cached != null && now - cached.atMs < statusesCacheTtlMs) return@withLock cached.statusByEvseId
        val text = fetchGzipRequired(statusesUrl, "Digitraffic AFIR statuses")
        val map = parseStatusesJson(text)
        statusesCache = CachedStatuses(map, now)
        map
    }

    private suspend fun fetchGzipRequired(url: String, label: String): String {
        val response = client.get(url) {
            // Digitraffic AFIR returns 406 without Accept-Encoding: gzip.
            header("Accept-Encoding", "gzip")
        }
        if (response.status.value !in 200..299) {
            val body = runCatching { response.bodyAsText() }.getOrElse { "<binary>" }
            throw NetworkException(response.status.value, "$label API error: $body")
        }
        return decodeBody(response.bodyAsBytes())
    }

    companion object {
        const val DEFAULT_LOCATIONS_URL =
            "https://afir.digitraffic.fi/api/charging-network/v1/locations/all"
        const val DEFAULT_STATUSES_URL =
            "https://afir.digitraffic.fi/api/charging-network/v1/locations/statuses/all"
    }

    private data class CachedLocations(val locations: List<DigitrafficAfirLocation>, val atMs: Long)
    private data class CachedStatuses(val statusByEvseId: Map<String, String>, val atMs: Long)
}

@Serializable
internal data class DigitrafficAfirLocationsResponse(
    val features: List<DigitrafficAfirFeature>? = null,
)

@Serializable
internal data class DigitrafficAfirFeature(
    val geometry: DigitrafficAfirGeometry? = null,
    val properties: DigitrafficAfirProperties? = null,
)

@Serializable
internal data class DigitrafficAfirGeometry(
    val coordinates: List<Double>? = null,
)

@Serializable
internal data class DigitrafficAfirProperties(
    val id: String? = null,
    val name: String? = null,
    val address: DigitrafficAfirAddress? = null,
    val evses: List<DigitrafficAfirEvse>? = null,
)

@Serializable
internal data class DigitrafficAfirAddress(
    val street: String? = null,
    val city: String? = null,
    val postalCode: String? = null,
    val countryCode: String? = null,
)

@Serializable
internal data class DigitrafficAfirEvse(
    val id: String? = null,
)

@Serializable
internal data class DigitrafficAfirStatusesResponse(
    val statuses: List<DigitrafficAfirStatusItem>? = null,
)

@Serializable
internal data class DigitrafficAfirStatusItem(
    val evseId: String? = null,
    val status: String? = null,
)

data class DigitrafficAfirLocation(
    val id: String,
    val latitude: Double?,
    val longitude: Double?,
    val address: String? = null,
    val evseIds: List<String> = emptyList(),
)

data class DigitrafficAfirPdcRecord(
    val id: String,
    val statusRaw: String,
    val latitude: Double,
    val longitude: Double,
    val stationId: String? = null,
    val address: String? = null,
    val distanceKm: Double = 0.0,
)
