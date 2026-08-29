package fr.geoking.gaston.api.austria

import fr.geoking.gaston.api.belib.AvailabilityStatus
import fr.geoking.gaston.api.common.OcpiEvseAvailability
import fr.geoking.gaston.shared.location.haversineKm
import fr.geoking.gaston.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Austria E-Control Ladestellenverzeichnis (charge) public API.
 *
 * Distinct from fuel Sprit (`api.e-control.at/sprit/1.0/` /
 * [fr.geoking.gaston.api.econtrol.AustriaEControlProvider]).
 *
 * Auth (free registration): header `Apikey` + `Referer: https://<registered-domain>`.
 * Live per-EVSE status is inline on `GET /search` (no separate status call).
 *
 * Docs: https://www.e-control.at/ladestellenverzeichnis-technische-informationen
 * Register: https://admin.ladestellen.at/#/api/registrieren
 */
class AustriaEControlEvClient(
    private val client: HttpClient,
    private val apiKey: String,
    private val refererDomain: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val cacheTtlMs: Long = 60_000L,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var cache: CachedSearch? = null

    /**
     * Returns PDC availability near [latitude]/[longitude] within [radiusKm], up to [limit].
     * Empty when [apiKey] or [refererDomain] is blank. Skips REMOVED EVSEs and inactive stations.
     */
    suspend fun getAvailability(
        latitude: Double,
        longitude: Double,
        radiusKm: Int = 15,
        limit: Int = 200,
    ): List<AustriaEControlEvPdcRecord> {
        if (apiKey.isBlank() || refererDomain.isBlank()) return emptyList()
        val stations = getOrFetchStations(latitude, longitude)
        return filterAvailability(
            stations = stations,
            latitude = latitude,
            longitude = longitude,
            radiusKm = radiusKm,
            limit = limit,
        )
    }

    internal fun filterAvailability(
        stations: List<AustriaEControlEvStation>,
        latitude: Double,
        longitude: Double,
        radiusKm: Int,
        limit: Int,
    ): List<AustriaEControlEvPdcRecord> {
        if (stations.isEmpty()) return emptyList()
        return stations.asSequence()
            .filter { (it.stationStatus ?: "ACTIVE").equals("ACTIVE", ignoreCase = true) }
            .flatMap { station ->
                val stationLat = station.latitude
                    ?: station.location?.lat
                val stationLon = station.longitude
                    ?: station.location?.lon
                val stationDist = station.distance
                val address = listOfNotNull(station.street, station.postCode, station.city)
                    .joinToString(", ")
                    .ifBlank { null }
                val stationId = listOfNotNull(
                    station.countryId ?: "AT",
                    station.operatorId,
                    station.stationId,
                ).joinToString("/").takeIf { station.stationId != null }

                (station.points.orEmpty()).asSequence().mapNotNull { point ->
                    val id = point.evseId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val statusRaw = point.status?.trim().orEmpty()
                    if (OcpiEvseAvailability.isRemoved(statusRaw)) return@mapNotNull null

                    val lat = point.latitude
                        ?: point.location?.lat
                        ?: stationLat
                        ?: return@mapNotNull null
                    val lon = point.longitude
                        ?: point.location?.lon
                        ?: stationLon
                        ?: return@mapNotNull null

                    val dist = stationDist?.takeIf { it >= 0 }
                        ?: haversineKm(latitude, longitude, lat, lon)
                    if (dist > radiusKm) return@mapNotNull null

                    AustriaEControlEvPdcRecord(
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

    internal fun parseSearchJson(text: String): List<AustriaEControlEvStation> {
        if (text.isBlank()) return emptyList()
        return json.decodeFromString(text)
    }

    /**
     * Maps E-Control RefillPointStatusEnum (OCPI-like + `OCCUPIED`) to app statuses.
     * Also accepts `OUT_OF_ORDER` (DATEX spelling).
     */
    internal fun mapStatus(statusRaw: String?): AvailabilityStatus {
        val s = statusRaw?.trim()?.uppercase()?.replace("_", "").orEmpty()
        return when (s) {
            "OCCUPIED" -> AvailabilityStatus.Occupied
            "OUTOFORDER" -> AvailabilityStatus.Maintenance
            else -> OcpiEvseAvailability.mapStatus(statusRaw)
        }
    }

    private suspend fun getOrFetchStations(
        latitude: Double,
        longitude: Double,
    ): List<AustriaEControlEvStation> = mutex.withLock {
        val key = CacheKey(
            lat = (latitude * 100).toInt(),
            lon = (longitude * 100).toInt(),
        )
        val cached = cache
        val now = nowMs()
        if (cached != null && cached.key == key && now - cached.atMs < cacheTtlMs) {
            return@withLock cached.stations
        }
        val response = client.get("$baseUrl/search") {
            header("Apikey", apiKey)
            header(HttpHeaders.Referrer, "https://${refererDomain.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')}")
            header(HttpHeaders.Accept, "application/json")
            parameter("latitude", latitude)
            parameter("longitude", longitude)
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw NetworkException(
                response.status.value,
                "E-Control charge /search error: $body",
            )
        }
        val stations = parseSearchJson(body)
        cache = CachedSearch(key, stations, now)
        stations
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.e-control.at/charge/1.0"
    }

    private data class CacheKey(val lat: Int, val lon: Int)
    private data class CachedSearch(
        val key: CacheKey,
        val stations: List<AustriaEControlEvStation>,
        val atMs: Long,
    )
}

@Serializable
data class AustriaEControlEvStation(
    val countryId: String? = null,
    val operatorId: String? = null,
    val stationId: String? = null,
    val stationStatus: String? = null,
    val label: String? = null,
    val postCode: String? = null,
    val city: String? = null,
    val street: String? = null,
    val distance: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val location: AustriaEControlEvLatLon? = null,
    val points: List<AustriaEControlEvPoint>? = null,
)

@Serializable
data class AustriaEControlEvPoint(
    val evseId: String? = null,
    val status: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val location: AustriaEControlEvLatLon? = null,
)

@Serializable
data class AustriaEControlEvLatLon(
    val lat: Double? = null,
    val lon: Double? = null,
)

data class AustriaEControlEvPdcRecord(
    val id: String,
    val statusRaw: String,
    val latitude: Double,
    val longitude: Double,
    val stationId: String? = null,
    val address: String? = null,
    val distanceKm: Double = 0.0,
)
