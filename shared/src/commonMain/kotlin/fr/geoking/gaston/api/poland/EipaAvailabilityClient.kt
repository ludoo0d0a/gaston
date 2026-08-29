package fr.geoking.gaston.api.poland

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
 * Poland EIPA (UDT) EVSE availability via free reader JSON dumps
 * (`point` + `station` + `dynamic`).
 *
 * Export URL pattern (documented at [eipa.udt.gov.pl/reader/docs](https://eipa.udt.gov.pl/reader/docs)):
 * `https://eipa.udt.gov.pl/reader/export-data/{resource}/{exportKey}`
 * where [resource] is `dynamic`, `point`, `station`, `pool`, `operator`, or `dictionary`.
 *
 * Free reader registration issues a personal export key + hourly rate limits.
 * [DEFAULT_EXPORT_KEY] is the public key used by the official EIPA map reader
 * (same approach as open-source importers); override via [exportKey] / `EIPA_EXPORT_KEY`.
 */
class EipaAvailabilityClient(
    private val client: HttpClient,
    private val exportKey: String = DEFAULT_EXPORT_KEY,
    private val exportBaseUrl: String = DEFAULT_EXPORT_BASE_URL,
    private val cacheTtlMs: Long = 180_000L,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var cache: CachedSnapshot? = null

    /**
     * Returns PDC availability near [latitude]/[longitude] within [radiusKm], up to [limit].
     * Empty when [exportKey] is blank. Skips non-electric points and entries without status.
     */
    suspend fun getAvailability(
        latitude: Double,
        longitude: Double,
        radiusKm: Int = 15,
        limit: Int = 200,
    ): List<EipaPdcRecord> {
        if (exportKey.isBlank()) return emptyList()
        val snapshot = getOrFetchSnapshot()
        return filterAvailability(
            snapshot = snapshot,
            latitude = latitude,
            longitude = longitude,
            radiusKm = radiusKm,
            limit = limit,
        )
    }

    internal fun filterAvailability(
        snapshot: EipaSnapshot,
        latitude: Double,
        longitude: Double,
        radiusKm: Int,
        limit: Int,
    ): List<EipaPdcRecord> {
        if (snapshot.dynamic.isEmpty() || snapshot.pointsById.isEmpty() || snapshot.stationsById.isEmpty()) {
            return emptyList()
        }
        return snapshot.dynamic.asSequence()
            .mapNotNull { dyn ->
                val status = dyn.status ?: return@mapNotNull null
                val point = snapshot.pointsById[dyn.pointId] ?: return@mapNotNull null
                if (!point.isElectric) return@mapNotNull null
                val station = snapshot.stationsById[point.stationId] ?: return@mapNotNull null
                if (station.suspended == true) return@mapNotNull null
                if (station.type != null && !station.type.equals("E", ignoreCase = true)) {
                    return@mapNotNull null
                }
                val lat = station.latitude ?: return@mapNotNull null
                val lon = station.longitude ?: return@mapNotNull null
                val dist = haversineKm(latitude, longitude, lat, lon)
                if (dist > radiusKm) return@mapNotNull null
                val id = dyn.code?.takeIf { it.isNotBlank() }
                    ?: point.code?.takeIf { it.isNotBlank() }
                    ?: dyn.pointId.toString()
                EipaPdcRecord(
                    id = id,
                    availability = status.availability,
                    freeStatus = status.status,
                    latitude = lat,
                    longitude = lon,
                    stationId = station.id?.toString(),
                    address = station.addressLabel(),
                    distanceKm = dist,
                )
            }
            .sortedBy { it.distanceKm }
            .take(limit)
            .toList()
    }

    /**
     * EIPA status:
     * - `availability` 1 = operationally available, 0 = not available
     * - `status` 1 = free (wolny), 0 = occupied (zajęty)
     */
    internal fun mapStatus(availability: Int?, freeStatus: Int?): AvailabilityStatus {
        if (availability == 0) return AvailabilityStatus.Maintenance
        return when (freeStatus) {
            1 -> AvailabilityStatus.Available
            0 -> AvailabilityStatus.Occupied
            else -> AvailabilityStatus.Unknown
        }
    }

    internal fun parseDynamicJson(text: String): List<EipaDynamicPoint> {
        if (text.isBlank()) return emptyList()
        return json.decodeFromString<EipaDataEnvelope<EipaDynamicPoint>>(text).data.orEmpty()
    }

    internal fun parsePointJson(text: String): List<EipaPoint> {
        if (text.isBlank()) return emptyList()
        return json.decodeFromString<EipaDataEnvelope<EipaPoint>>(text).data.orEmpty()
    }

    internal fun parseStationJson(text: String): List<EipaStation> {
        if (text.isBlank()) return emptyList()
        return json.decodeFromString<EipaDataEnvelope<EipaStation>>(text).data.orEmpty()
    }

    internal fun buildSnapshot(
        dynamic: List<EipaDynamicPoint>,
        points: List<EipaPoint>,
        stations: List<EipaStation>,
    ): EipaSnapshot {
        return EipaSnapshot(
            dynamic = dynamic,
            pointsById = points.mapNotNull { p ->
                val id = p.id ?: return@mapNotNull null
                id to p
            }.toMap(),
            stationsById = stations.mapNotNull { s ->
                val id = s.id ?: return@mapNotNull null
                id to s
            }.toMap(),
        )
    }

    private suspend fun getOrFetchSnapshot(): EipaSnapshot = mutex.withLock {
        val cached = cache
        val now = nowMs()
        if (cached != null && now - cached.atMs < cacheTtlMs) return@withLock cached.snapshot

        val dynamicText = fetchResource("dynamic")
        val pointText = fetchResource("point")
        val stationText = fetchResource("station")
        val snapshot = buildSnapshot(
            dynamic = parseDynamicJson(dynamicText),
            points = parsePointJson(pointText),
            stations = parseStationJson(stationText),
        )
        cache = CachedSnapshot(snapshot, now)
        snapshot
    }

    private suspend fun fetchResource(resource: String): String {
        val url = "$exportBaseUrl/$resource/$exportKey"
        val response = client.get(url)
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw NetworkException(response.status.value, "EIPA $resource export error: $body")
        }
        return body
    }

    companion object {
        const val DEFAULT_EXPORT_BASE_URL = "https://eipa.udt.gov.pl/reader/export-data"

        /**
         * Public export key used by the official EIPA map reader.
         * Prefer a personal key from https://eipa.udt.gov.pl/reader/register when rate-limited.
         */
        const val DEFAULT_EXPORT_KEY = "cc00241029ceddb4013bf2e166193882"
    }

    private data class CachedSnapshot(val snapshot: EipaSnapshot, val atMs: Long)
}

data class EipaSnapshot(
    val dynamic: List<EipaDynamicPoint>,
    val pointsById: Map<Int, EipaPoint>,
    val stationsById: Map<Int, EipaStation>,
)

@Serializable
internal data class EipaDataEnvelope<T>(
    val data: List<T>? = null,
    val generated: String? = null,
)

@Serializable
data class EipaDynamicPoint(
    @SerialName("point_id") val pointId: Int,
    val code: String? = null,
    val status: EipaPointStatus? = null,
)

@Serializable
data class EipaPointStatus(
    val availability: Int? = null,
    val status: Int? = null,
    val ts: String? = null,
)

@Serializable
data class EipaPoint(
    val id: Int? = null,
    val code: String? = null,
    @SerialName("station_id") val stationId: Int? = null,
    @SerialName("charging_solutions") val chargingSolutions: List<EipaChargingSolution>? = null,
    val connectors: List<EipaConnector>? = null,
) {
    val isElectric: Boolean
        get() = !chargingSolutions.isNullOrEmpty() || !connectors.isNullOrEmpty()
}

@Serializable
data class EipaChargingSolution(
    val mode: Int? = null,
    val power: Double? = null,
)

@Serializable
data class EipaConnector(
    val interfaces: List<Int>? = null,
    val power: Double? = null,
    @SerialName("cable_attached") val cableAttached: Boolean? = null,
)

@Serializable
data class EipaStation(
    val id: Int? = null,
    @SerialName("pool_id") val poolId: Int? = null,
    val type: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val suspended: Boolean? = null,
    val location: EipaStationLocation? = null,
) {
    fun addressLabel(): String? =
        listOfNotNull(location?.city, location?.province).joinToString(", ").ifBlank { null }
}

@Serializable
data class EipaStationLocation(
    val city: String? = null,
    val community: String? = null,
    val district: String? = null,
    val province: String? = null,
)

data class EipaPdcRecord(
    val id: String,
    val availability: Int?,
    val freeStatus: Int?,
    val latitude: Double,
    val longitude: Double,
    val stationId: String? = null,
    val address: String? = null,
    val distanceKm: Double = 0.0,
)
