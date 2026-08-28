package fr.geoking.gaston.api.belgiumnap

import fr.geoking.gaston.api.belib.AvailabilityStatus
import fr.geoking.gaston.shared.location.haversineKm
import fr.geoking.gaston.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Belgium NAP (transportdata.be) EVSE availability via the open
 * [Road Public Charging Network](https://transportdata.be/dataset/road-public-charging-network)
 * OCPI-style locations dump (status embedded per EVSE). No API key.
 *
 * Note: Eco-Movement DATEX endpoints on the same NAP (`nap-be.eco-movement.com`) require auth;
 * this client uses the free Road.io resource registered on the NAP instead.
 */
class BelgiumNapAvailabilityClient(
    private val client: HttpClient,
    private val locationsUrl: String = DEFAULT_LOCATIONS_URL,
    private val cacheTtlMs: Long = 60_000L,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
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
        limit: Int = 200
    ): List<BelgiumNapPdcRecord> {
        return filterAvailability(
            locations = getOrFetchLocations(),
            latitude = latitude,
            longitude = longitude,
            radiusKm = radiusKm,
            limit = limit
        )
    }

    internal fun filterAvailability(
        locations: List<BelgiumNapLocation>,
        latitude: Double,
        longitude: Double,
        radiusKm: Int,
        limit: Int
    ): List<BelgiumNapPdcRecord> {
        if (locations.isEmpty()) return emptyList()

        return locations.asSequence()
            .flatMap { loc ->
                val lat = loc.coordinates?.latitude?.toDoubleOrNull() ?: return@flatMap emptySequence()
                val lon = loc.coordinates?.longitude?.toDoubleOrNull() ?: return@flatMap emptySequence()
                val dist = haversineKm(latitude, longitude, lat, lon)
                if (dist > radiusKm) return@flatMap emptySequence()
                (loc.evses.orEmpty()).asSequence().mapNotNull { evse ->
                    val id = evse.evseId?.takeIf { it.isNotBlank() } ?: evse.uid?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                    val statusRaw = evse.status?.trim().orEmpty()
                    if (statusRaw.equals("REMOVED", ignoreCase = true)) return@mapNotNull null
                    BelgiumNapPdcRecord(
                        id = id,
                        statusRaw = statusRaw,
                        latitude = lat,
                        longitude = lon,
                        stationId = loc.id,
                        address = listOfNotNull(loc.address, loc.city).joinToString(", ").ifBlank { null },
                        distanceKm = dist
                    )
                }
            }
            .sortedBy { it.distanceKm }
            .take(limit)
            .toList()
    }

    internal fun parseLocationsJson(text: String): List<BelgiumNapLocation> {
        if (text.isBlank()) return emptyList()
        return json.decodeFromString(text)
    }

    internal fun mapStatus(statusRaw: String): AvailabilityStatus {
        val s = statusRaw.trim().uppercase()
        return when (s) {
            "AVAILABLE" -> AvailabilityStatus.Available
            "CHARGING", "BLOCKED" -> AvailabilityStatus.Occupied
            "RESERVED" -> AvailabilityStatus.Reserved
            "INOPERATIVE", "OUTOFORDER" -> AvailabilityStatus.Maintenance
            "PLANNED" -> AvailabilityStatus.PlannedIntoService
            "REMOVED" -> AvailabilityStatus.Removed
            "UNKNOWN", "" -> AvailabilityStatus.Unknown
            else -> AvailabilityStatus.Unknown
        }
    }

    private suspend fun getOrFetchLocations(): List<BelgiumNapLocation> = mutex.withLock {
        val cached = cache
        val now = nowMs()
        if (cached != null && now - cached.atMs < cacheTtlMs) return@withLock cached.locations
        val response = client.get(locationsUrl)
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw NetworkException(response.status.value, "Belgium NAP locations API error: $body")
        }
        val locations = parseLocationsJson(body)
        cache = CachedLocations(locations, now)
        locations
    }

    companion object {
        const val DEFAULT_LOCATIONS_URL =
            "https://roaming.road.io/files/9ef09c78-2666-418a-aa45-4f2261e2e305/locations.json?force=true"
    }

    private data class CachedLocations(val locations: List<BelgiumNapLocation>, val atMs: Long)
}

@Serializable
data class BelgiumNapLocation(
    val id: String? = null,
    val name: String? = null,
    val address: String? = null,
    val city: String? = null,
    val coordinates: BelgiumNapCoordinates? = null,
    val evses: List<BelgiumNapEvse>? = null,
)

@Serializable
data class BelgiumNapCoordinates(
    val latitude: String? = null,
    val longitude: String? = null,
)

@Serializable
data class BelgiumNapEvse(
    val uid: String? = null,
    @SerialName("evse_id") val evseId: String? = null,
    val status: String? = null,
)

data class BelgiumNapPdcRecord(
    val id: String,
    val statusRaw: String,
    val latitude: Double,
    val longitude: Double,
    val stationId: String? = null,
    val address: String? = null,
    val distanceKm: Double = 0.0,
)
