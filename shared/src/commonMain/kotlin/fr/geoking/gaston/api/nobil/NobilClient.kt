package fr.geoking.gaston.api.nobil

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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Shared NOBIL (Enova) client for Norway / Sweden charging-station dumps.
 *
 * Datadump: `https://nobil.no/api/server/datadump.php` (API key required, CC-BY).
 * Without [apiKey], all fetch methods return empty lists.
 *
 * Sweden reuses this client with [countryCode] `SWE`. Real-time WebSocket status is a
 * follow-up — see `docs/NOBIL_AVAILABILITY.md`.
 */
class NobilClient(
    private val client: HttpClient,
    private val apiKey: String,
    private val countryCode: String = DEFAULT_COUNTRY_NOR,
    private val baseUrl: String = DEFAULT_DATADUMP_URL,
    private val cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var cache: CachedStations? = null

    /**
     * Full country dump (cached). Empty when [apiKey] is blank or the response has no stations.
     */
    suspend fun getStations(): List<NobilStation> {
        if (apiKey.isBlank()) return emptyList()
        return getOrFetchStations()
    }

    /**
     * Per-connector availability near [latitude]/[longitude] within [radiusKm], up to [limit].
     * Maps OCPI-like / NOBIL connector status when present; skips REMOVED and inactive stations.
     */
    suspend fun getAvailability(
        latitude: Double,
        longitude: Double,
        radiusKm: Int = 15,
        limit: Int = 200,
    ): List<NobilPdcRecord> {
        if (apiKey.isBlank()) return emptyList()
        return filterAvailability(
            stations = getOrFetchStations(),
            latitude = latitude,
            longitude = longitude,
            radiusKm = radiusKm,
            limit = limit,
        )
    }

    internal fun filterAvailability(
        stations: List<NobilStation>,
        latitude: Double,
        longitude: Double,
        radiusKm: Int,
        limit: Int,
    ): List<NobilPdcRecord> {
        if (stations.isEmpty()) return emptyList()

        return stations.asSequence()
            .mapNotNull { station ->
                val meta = station.csmd ?: return@mapNotNull null
                if (meta.stationStatus != null && meta.stationStatus != 1) return@mapNotNull null
                val (lat, lon) = parsePosition(meta.position) ?: return@mapNotNull null
                val dist = haversineKm(latitude, longitude, lat, lon)
                if (dist > radiusKm) return@mapNotNull null
                Triple(station, lat to lon, dist)
            }
            .flatMap { (station, coords, dist) ->
                val meta = station.csmd!!
                val (lat, lon) = coords
                val stationId = meta.internationalId?.takeIf { it.isNotBlank() }
                    ?: meta.id?.toString()
                val address = listOfNotNull(
                    listOfNotNull(meta.street, meta.houseNumber).joinToString(" ").ifBlank { null },
                    meta.city,
                ).joinToString(", ").ifBlank { null }

                val connectors = station.attr?.conn.orEmpty()
                if (connectors.isEmpty()) {
                    // Station-level counts only — no per-EVSE ids; skip for PDC list.
                    return@flatMap emptySequence()
                }
                connectors.asSequence().mapNotNull { (connKey, attrs) ->
                    val statusRaw = connectorStatusRaw(attrs)
                    if (OcpiEvseAvailability.isRemoved(statusRaw)) return@mapNotNull null
                    val id = connectorId(attrs, stationId, connKey) ?: return@mapNotNull null
                    NobilPdcRecord(
                        id = id,
                        statusRaw = statusRaw,
                        latitude = lat,
                        longitude = lon,
                        stationId = stationId,
                        address = address,
                        distanceKm = dist,
                    )
                }
            }
            .sortedBy { it.distanceKm }
            .take(limit)
            .toList()
    }

    internal fun parseDatadumpJson(text: String): List<NobilStation> {
        if (text.isBlank()) return emptyList()
        val envelope = json.decodeFromString<NobilDatadumpEnvelope>(text)
        return envelope.chargerStations.orEmpty()
    }

    internal fun mapStatus(statusRaw: String?): AvailabilityStatus =
        OcpiEvseAvailability.mapStatus(normalizeStatusRaw(statusRaw))

    private suspend fun getOrFetchStations(): List<NobilStation> = mutex.withLock {
        val cached = cache
        val now = nowMs()
        if (cached != null && now - cached.atMs < cacheTtlMs) return@withLock cached.stations
        val response = client.get(baseUrl) {
            parameter("apikey", apiKey)
            parameter("countrycode", countryCode)
            parameter("format", "json")
            parameter("file", "false")
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw NetworkException(response.status.value, "NOBIL datadump error: $body")
        }
        val stations = parseDatadumpJson(body)
        cache = CachedStations(stations, now)
        stations
    }

    companion object {
        const val DEFAULT_DATADUMP_URL = "https://nobil.no/api/server/datadump.php"
        const val DEFAULT_COUNTRY_NOR = "NOR"
        const val COUNTRY_SWE = "SWE"
        /** Server caches static dumps ~1h; keep a shorter client TTL for availability freshness. */
        const val DEFAULT_CACHE_TTL_MS = 15 * 60_000L

        /** Attr type ids — https://nobil.no/admin/attributes.php */
        private const val ATTR_CONNECTOR_STATUS = "8"
        private const val ATTR_CONNECTOR_ERROR = "9"
        private const val ATTR_EVSE_UID = "27"
        private const val ATTR_EVSE_ID = "28"

        internal fun parsePosition(raw: String?): Pair<Double, Double>? {
            if (raw.isNullOrBlank()) return null
            val cleaned = raw.trim().removePrefix("(").removeSuffix(")")
            val parts = cleaned.split(',').map { it.trim() }
            if (parts.size < 2) return null
            val lat = parts[0].toDoubleOrNull() ?: return null
            val lon = parts[1].toDoubleOrNull() ?: return null
            return lat to lon
        }

        internal fun connectorStatusRaw(attrs: Map<String, NobilAttr>): String {
            val error = attrs[ATTR_CONNECTOR_ERROR]
            if (error?.attrValId == "1" ||
                error?.trans?.contains("out of service", ignoreCase = true) == true
            ) {
                return "OUTOFORDER"
            }
            val status = attrs[ATTR_CONNECTOR_STATUS] ?: return "UNKNOWN"
            val fromVal = attrString(status.attrVal)?.trim().orEmpty()
            if (fromVal.isNotEmpty() && looksLikeOcpiStatus(fromVal)) return fromVal.uppercase()
            val fromTrans = status.trans?.trim().orEmpty()
            if (fromTrans.isNotEmpty() && looksLikeOcpiStatus(fromTrans)) return fromTrans.uppercase()
            return when (status.attrValId) {
                "0" -> when {
                    fromTrans.equals("Vacant", ignoreCase = true) || fromTrans.isEmpty() -> "AVAILABLE"
                    else -> fromTrans.uppercase()
                }
                "1" -> "CHARGING"
                "2" -> "RESERVED"
                else -> when {
                    fromTrans.equals("Vacant", ignoreCase = true) -> "AVAILABLE"
                    fromTrans.contains("Busy", ignoreCase = true) -> "CHARGING"
                    fromTrans.equals("Reserved", ignoreCase = true) -> "RESERVED"
                    fromTrans.isNotEmpty() -> fromTrans.uppercase()
                    else -> "UNKNOWN"
                }
            }
        }

        private fun looksLikeOcpiStatus(s: String): Boolean {
            val u = s.uppercase()
            return u in setOf(
                "AVAILABLE", "CHARGING", "BLOCKED", "RESERVED", "INOPERATIVE",
                "OUTOFORDER", "PLANNED", "REMOVED", "UNKNOWN", "FREE", "IDLE",
            )
        }

        private fun normalizeStatusRaw(statusRaw: String?): String? {
            val s = statusRaw?.trim().orEmpty()
            if (s.isEmpty()) return null
            return when {
                s.equals("Vacant", ignoreCase = true) -> "AVAILABLE"
                s.contains("Busy", ignoreCase = true) -> "CHARGING"
                else -> s
            }
        }

        private fun connectorId(
            attrs: Map<String, NobilAttr>,
            stationId: String?,
            connKey: String,
        ): String? {
            attrString(attrs[ATTR_EVSE_ID]?.attrVal)?.takeIf { it.isNotBlank() }?.let { return it }
            attrString(attrs[ATTR_EVSE_UID]?.attrVal)?.takeIf { it.isNotBlank() }?.let { return it }
            if (stationId.isNullOrBlank()) return null
            return "$stationId-$connKey"
        }

        private fun attrString(el: JsonElement?): String? {
            val prim = el as? JsonPrimitive ?: return null
            return prim.contentOrNull?.takeIf { it.isNotBlank() }
        }
    }

    private data class CachedStations(val stations: List<NobilStation>, val atMs: Long)
}

@Serializable
data class NobilDatadumpEnvelope(
    @SerialName("Provider") val provider: String? = null,
    @SerialName("Rights") val rights: String? = null,
    val apiver: String? = null,
    @SerialName("chargerstations") val chargerStations: List<NobilStation>? = null,
)

@Serializable
data class NobilStation(
    val csmd: NobilStationMeta? = null,
    val attr: NobilStationAttrs? = null,
)

@Serializable
data class NobilStationMeta(
    val id: Long? = null,
    val name: String? = null,
    @SerialName("Street") val street: String? = null,
    @SerialName("House_number") val houseNumber: String? = null,
    @SerialName("Zipcode") val zipcode: String? = null,
    @SerialName("City") val city: String? = null,
    @SerialName("Owned_by") val ownedBy: String? = null,
    @SerialName("Operator") val operator: String? = null,
    @SerialName("Number_charging_points") val numberChargingPoints: Int? = null,
    @SerialName("Position") val position: String? = null,
    @SerialName("Available_charging_points") val availableChargingPoints: Int? = null,
    @SerialName("Station_status") val stationStatus: Int? = null,
    @SerialName("Land_code") val landCode: String? = null,
    @SerialName("International_id") val internationalId: String? = null,
)

@Serializable
data class NobilStationAttrs(
    val st: Map<String, NobilAttr>? = null,
    val conn: Map<String, Map<String, NobilAttr>>? = null,
)

@Serializable
data class NobilAttr(
    @SerialName("attrtypeid") val attrTypeId: String? = null,
    @SerialName("attrname") val attrName: String? = null,
    @SerialName("attrvalid") val attrValId: String? = null,
    @SerialName("trans") val trans: String? = null,
    @SerialName("attrval") val attrVal: JsonElement? = null,
)

data class NobilPdcRecord(
    val id: String,
    val statusRaw: String,
    val latitude: Double,
    val longitude: Double,
    val stationId: String? = null,
    val address: String? = null,
    val distanceKm: Double = 0.0,
)
