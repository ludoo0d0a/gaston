package fr.geoking.gaston.api.switzerland

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
 * Switzerland ich-tanke-strom (BFE) EVSE availability via open static + status JSON dumps
 * ([opendata.swiss](https://opendata.swiss/en/dataset/ladestationen)).
 * OICP-inspired format — not OCPI. No API key.
 *
 * Docs: https://github.com/SFOE/ichtankestrom_Documentation
 */
class IchTankeStromAvailabilityClient(
    private val client: HttpClient,
    private val staticUrl: String = DEFAULT_STATIC_URL,
    private val statusUrl: String = DEFAULT_STATUS_URL,
    private val statusCacheTtlMs: Long = 60_000L,
    private val staticCacheTtlMs: Long = 3_600_000L,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var staticCache: CachedStatic? = null
    private var statusCache: CachedStatus? = null

    /**
     * Returns PDC availability near [latitude]/[longitude] within [radiusKm], up to [limit].
     * Skips EVSEs with status EvseNotFound.
     */
    suspend fun getAvailability(
        latitude: Double,
        longitude: Double,
        radiusKm: Int = 15,
        limit: Int = 200,
    ): List<IchTankeStromPdcRecord> {
        return filterAvailability(
            staticByEvse = getOrFetchStatic(),
            statusByEvse = getOrFetchStatus(),
            latitude = latitude,
            longitude = longitude,
            radiusKm = radiusKm,
            limit = limit,
        )
    }

    internal fun filterAvailability(
        staticByEvse: Map<String, IchTankeStromStaticGeo>,
        statusByEvse: Map<String, String>,
        latitude: Double,
        longitude: Double,
        radiusKm: Int,
        limit: Int,
    ): List<IchTankeStromPdcRecord> {
        if (staticByEvse.isEmpty()) return emptyList()

        return staticByEvse.asSequence()
            .mapNotNull { (evseId, geo) ->
                val statusRaw = statusByEvse[evseId]?.trim().orEmpty()
                if (statusRaw.equals("EvseNotFound", ignoreCase = true)) return@mapNotNull null
                val dist = haversineKm(latitude, longitude, geo.latitude, geo.longitude)
                if (dist > radiusKm) return@mapNotNull null
                IchTankeStromPdcRecord(
                    id = evseId,
                    statusRaw = statusRaw,
                    latitude = geo.latitude,
                    longitude = geo.longitude,
                    stationId = geo.stationId,
                    address = geo.address,
                    distanceKm = dist,
                )
            }
            .sortedBy { it.distanceKm }
            .take(limit)
            .toList()
    }

    internal fun parseStaticJson(text: String): Map<String, IchTankeStromStaticGeo> {
        if (text.isBlank()) return emptyMap()
        val root = json.decodeFromString<IchTankeStromStaticRoot>(text)
        val out = HashMap<String, IchTankeStromStaticGeo>(16_384)
        for (operator in root.evseData.orEmpty()) {
            for (record in operator.records.orEmpty()) {
                val id = record.evseId?.takeIf { it.isNotBlank() } ?: continue
                val (lat, lon) = parseGoogleCoords(record.geoCoordinates?.google) ?: continue
                val address = listOfNotNull(
                    record.address?.street?.takeIf { it.isNotBlank() },
                    record.address?.city?.takeIf { it.isNotBlank() },
                ).joinToString(", ").ifBlank { null }
                out[id] = IchTankeStromStaticGeo(
                    stationId = record.chargingStationId?.takeIf { it.isNotBlank() },
                    latitude = lat,
                    longitude = lon,
                    address = address,
                )
            }
        }
        return out
    }

    internal fun parseStatusJson(text: String): Map<String, String> {
        if (text.isBlank()) return emptyMap()
        val root = json.decodeFromString<IchTankeStromStatusRoot>(text)
        val out = HashMap<String, String>(16_384)
        for (operator in root.evseStatuses.orEmpty()) {
            for (record in operator.records.orEmpty()) {
                val id = record.evseId?.takeIf { it.isNotBlank() } ?: continue
                out[id] = record.status?.trim().orEmpty()
            }
        }
        return out
    }

    /** Maps OICP EvseStatus → app [AvailabilityStatus]. */
    internal fun mapStatus(statusRaw: String): AvailabilityStatus {
        return when (statusRaw.trim().lowercase()) {
            "available" -> AvailabilityStatus.Available
            "occupied" -> AvailabilityStatus.Occupied
            "reserved" -> AvailabilityStatus.Reserved
            "outofservice" -> AvailabilityStatus.Maintenance
            "evsenotfound" -> AvailabilityStatus.Removed
            "unknown", "" -> AvailabilityStatus.Unknown
            else -> AvailabilityStatus.Unknown
        }
    }

    private suspend fun getOrFetchStatic(): Map<String, IchTankeStromStaticGeo> = mutex.withLock {
        val cached = staticCache
        val now = nowMs()
        if (cached != null && now - cached.atMs < staticCacheTtlMs) return@withLock cached.byEvse
        val text = fetchText(staticUrl, "ich-tanke-strom static")
        val byEvse = parseStaticJson(text)
        staticCache = CachedStatic(byEvse, now)
        byEvse
    }

    private suspend fun getOrFetchStatus(): Map<String, String> = mutex.withLock {
        val cached = statusCache
        val now = nowMs()
        if (cached != null && now - cached.atMs < statusCacheTtlMs) return@withLock cached.byEvse
        val text = fetchText(statusUrl, "ich-tanke-strom status")
        val byEvse = parseStatusJson(text)
        statusCache = CachedStatus(byEvse, now)
        byEvse
    }

    private suspend fun fetchText(url: String, label: String): String {
        val response = client.get(url)
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw NetworkException(response.status.value, "$label API error: $body")
        }
        return body
    }

    companion object {
        const val DEFAULT_STATIC_URL =
            "https://data.geo.admin.ch/ch.bfe.ladestellen-elektromobilitaet/data/ch.bfe.ladestellen-elektromobilitaet.json"
        const val DEFAULT_STATUS_URL =
            "https://data.geo.admin.ch/ch.bfe.ladestellen-elektromobilitaet/status/ch.bfe.ladestellen-elektromobilitaet.json"

        /** OICP Google coords: `"lat lon"` (space-separated). */
        internal fun parseGoogleCoords(raw: String?): Pair<Double, Double>? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.trim().split(Regex("\\s+"))
            if (parts.size < 2) return null
            if (parts[0].equals("None", ignoreCase = true)) return null
            val lat = parts[0].toDoubleOrNull() ?: return null
            val lon = parts[1].toDoubleOrNull() ?: return null
            return lat to lon
        }
    }

    private data class CachedStatic(val byEvse: Map<String, IchTankeStromStaticGeo>, val atMs: Long)
    private data class CachedStatus(val byEvse: Map<String, String>, val atMs: Long)
}

@Serializable
data class IchTankeStromStaticRoot(
    @SerialName("EVSEData") val evseData: List<IchTankeStromOperatorData>? = null,
)

@Serializable
data class IchTankeStromOperatorData(
    @SerialName("EVSEDataRecord") val records: List<IchTankeStromEvseData>? = null,
)

@Serializable
data class IchTankeStromEvseData(
    @SerialName("EvseID") val evseId: String? = null,
    @SerialName("ChargingStationId") val chargingStationId: String? = null,
    @SerialName("GeoCoordinates") val geoCoordinates: IchTankeStromGeoCoordinates? = null,
    @SerialName("Address") val address: IchTankeStromAddress? = null,
)

@Serializable
data class IchTankeStromGeoCoordinates(
    @SerialName("Google") val google: String? = null,
)

@Serializable
data class IchTankeStromAddress(
    @SerialName("Street") val street: String? = null,
    @SerialName("City") val city: String? = null,
)

@Serializable
data class IchTankeStromStatusRoot(
    @SerialName("EVSEStatuses") val evseStatuses: List<IchTankeStromOperatorStatus>? = null,
)

@Serializable
data class IchTankeStromOperatorStatus(
    @SerialName("EVSEStatusRecord") val records: List<IchTankeStromEvseStatus>? = null,
)

@Serializable
data class IchTankeStromEvseStatus(
    @SerialName("EvseID") val evseId: String? = null,
    @SerialName("EVSEStatus") val status: String? = null,
)

data class IchTankeStromStaticGeo(
    val stationId: String?,
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
)

data class IchTankeStromPdcRecord(
    val id: String,
    val statusRaw: String,
    val latitude: Double,
    val longitude: Double,
    val stationId: String? = null,
    val address: String? = null,
    val distanceKm: Double = 0.0,
)
