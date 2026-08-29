package fr.geoking.gaston.api.italy

import fr.geoking.gaston.api.belib.AvailabilityStatus
import fr.geoking.gaston.api.common.OcpiEvseAvailability
import fr.geoking.gaston.shared.location.haversineKm
import fr.geoking.gaston.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.PI
import kotlin.math.cos

/**
 * Italy PUN (Piattaforma Unica Nazionale) EVSE availability via the public ArcGIS
 * FeatureServer proxy `PdR_latest_new_public`. No API key.
 *
 * Probe notes (see `docs/ITALY_PUN_AVAILABILITY.md`): older `IdR_latest_ready` requires a
 * token; several newer utility proxies return 403. Status values are OCPI-like
 * (`AVAILABLE`, `CHARGING`, …) but field timestamps may lag — prefer Eco-Movement OCPI
 * for live status when both are available.
 */
class ItalyPunAvailabilityClient(
    private val client: HttpClient,
    private val queryUrl: String = DEFAULT_QUERY_URL,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    private val cacheTtlMs: Long = 60_000L,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var cache: CachedQuery? = null

    /**
     * Returns EVSE availability near [latitude]/[longitude] within [radiusKm], up to [limit].
     * Skips EVSEs with status REMOVED.
     */
    suspend fun getAvailability(
        latitude: Double,
        longitude: Double,
        radiusKm: Int = 15,
        limit: Int = 200,
    ): List<ItalyPunPdcRecord> {
        val features = getOrFetchFeatures(latitude, longitude, radiusKm)
        return filterAvailability(
            features = features,
            latitude = latitude,
            longitude = longitude,
            radiusKm = radiusKm,
            limit = limit,
        )
    }

    internal fun filterAvailability(
        features: List<ItalyPunFeatureAttributes>,
        latitude: Double,
        longitude: Double,
        radiusKm: Int,
        limit: Int,
    ): List<ItalyPunPdcRecord> {
        if (features.isEmpty()) return emptyList()
        return features.asSequence()
            .mapNotNull { attr ->
                val lat = attr.latitude ?: return@mapNotNull null
                val lon = attr.longitude ?: return@mapNotNull null
                val id = attr.idEvse?.takeIf { it.isNotBlank() }
                    ?: attr.idUnivoco?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val statusRaw = attr.stato?.trim().orEmpty()
                if (OcpiEvseAvailability.isRemoved(statusRaw)) return@mapNotNull null
                val dist = haversineKm(latitude, longitude, lat, lon)
                if (dist > radiusKm) return@mapNotNull null
                ItalyPunPdcRecord(
                    id = id,
                    statusRaw = statusRaw,
                    latitude = lat,
                    longitude = lon,
                    stationId = attr.idLocation,
                    address = listOfNotNull(attr.indirizzo, attr.citta).joinToString(", ").ifBlank { null },
                    distanceKm = dist,
                )
            }
            .sortedBy { it.distanceKm }
            .take(limit)
            .toList()
    }

    internal fun parseQueryJson(text: String): List<ItalyPunFeatureAttributes> {
        if (text.isBlank()) return emptyList()
        val response = json.decodeFromString<ItalyPunQueryResponse>(text)
        response.error?.let { err ->
            throw NetworkException(err.code, "Italy PUN ArcGIS error: ${err.message}")
        }
        return response.features.orEmpty().mapNotNull { it.attributes }
    }

    internal fun mapStatus(statusRaw: String): AvailabilityStatus =
        OcpiEvseAvailability.mapStatus(statusRaw)

    internal fun bboxWhereClause(latitude: Double, longitude: Double, radiusKm: Int): String {
        val latDelta = radiusKm / 111.0
        val cosLat = cos(latitude * PI / 180.0).let { c -> if (c < 0.2) 0.2 else c }
        val lonDelta = radiusKm / (111.0 * cosLat)
        val latMin = latitude - latDelta
        val latMax = latitude + latDelta
        val lonMin = longitude - lonDelta
        val lonMax = longitude + lonDelta
        return "Latitudine_EVSE BETWEEN $latMin AND $latMax AND Longitudine_EVSE BETWEEN $lonMin AND $lonMax"
    }

    private suspend fun getOrFetchFeatures(
        latitude: Double,
        longitude: Double,
        radiusKm: Int,
    ): List<ItalyPunFeatureAttributes> = mutex.withLock {
        val key = CacheKey(
            lat = (latitude * 100).toInt(),
            lon = (longitude * 100).toInt(),
            radiusKm = radiusKm,
        )
        val cached = cache
        val now = nowMs()
        if (cached != null && cached.key == key && now - cached.atMs < cacheTtlMs) {
            return@withLock cached.features
        }
        val where = bboxWhereClause(latitude, longitude, radiusKm)
        val features = ArrayList<ItalyPunFeatureAttributes>()
        var offset = 0
        while (true) {
            val page = fetchPage(where = where, resultOffset = offset)
            if (page.isEmpty()) break
            features.addAll(page)
            if (page.size < pageSize) break
            offset += pageSize
            // Safety: avoid unbounded pagination if the service misbehaves.
            if (offset >= MAX_FETCH) break
        }
        cache = CachedQuery(key, features, now)
        features
    }

    private suspend fun fetchPage(where: String, resultOffset: Int): List<ItalyPunFeatureAttributes> {
        val response = client.get(queryUrl) {
            parameter("where", where)
            parameter("outFields", OUT_FIELDS)
            parameter("returnGeometry", "false")
            parameter("resultOffset", resultOffset)
            parameter("resultRecordCount", pageSize)
            parameter("f", "json")
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw NetworkException(response.status.value, "Italy PUN ArcGIS HTTP error: $body")
        }
        return parseQueryJson(body)
    }

    companion object {
        /** Public utility proxy item `PdR_latest_new_public` (GSE ArcGIS org). */
        const val DEFAULT_QUERY_URL =
            "https://utility.arcgis.com/usrsvcs/servers/695bae597e5c4346b9c2f5923d88749d/rest/services/PdR_latest_new/FeatureServer/0/query"

        const val DEFAULT_PAGE_SIZE = 2000
        private const val MAX_FETCH = 10_000
        private const val OUT_FIELDS =
            "ID_EVSE,ID_univoco_EVSE,ID_location,Stato,Latitudine_EVSE,Longitudine_EVSE,Indirizzo,Città"
    }

    private data class CacheKey(val lat: Int, val lon: Int, val radiusKm: Int)
    private data class CachedQuery(
        val key: CacheKey,
        val features: List<ItalyPunFeatureAttributes>,
        val atMs: Long,
    )
}

@Serializable
internal data class ItalyPunQueryResponse(
    val features: List<ItalyPunFeature>? = null,
    val error: ItalyPunArcGisError? = null,
)

@Serializable
internal data class ItalyPunFeature(
    val attributes: ItalyPunFeatureAttributes? = null,
)

@Serializable
internal data class ItalyPunArcGisError(
    val code: Int? = null,
    val message: String? = null,
)

@Serializable
data class ItalyPunFeatureAttributes(
    @SerialName("ID_EVSE") val idEvse: String? = null,
    @SerialName("ID_univoco_EVSE") val idUnivoco: String? = null,
    @SerialName("ID_location") val idLocation: String? = null,
    @SerialName("Stato") val stato: String? = null,
    @SerialName("Latitudine_EVSE") val latitude: Double? = null,
    @SerialName("Longitudine_EVSE") val longitude: Double? = null,
    @SerialName("Indirizzo") val indirizzo: String? = null,
    @SerialName("Città") val citta: String? = null,
)

data class ItalyPunPdcRecord(
    val id: String,
    val statusRaw: String,
    val latitude: Double,
    val longitude: Double,
    val stationId: String? = null,
    val address: String? = null,
    val distanceKm: Double = 0.0,
)
