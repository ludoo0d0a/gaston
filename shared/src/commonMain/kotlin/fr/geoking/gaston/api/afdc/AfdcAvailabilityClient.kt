package fr.geoking.gaston.api.afdc

import fr.geoking.gaston.api.belib.AvailabilityStatus
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

/**
 * NREL / AFDC Alternative Fuel Stations API (US + Canada EV inventory).
 *
 * Docs: https://developer.nrel.gov/docs/transportation/alt-fuel-stations-v1/
 * (host may redirect to developer.nlr.gov). Free API key:
 * https://developer.nrel.gov/signup/
 *
 * Status is station-level (`status_code`), not full OCPI per-EVSE realtime.
 * Blank [apiKey] → empty results (no crash).
 */
class AfdcAvailabilityClient(
    private val client: HttpClient,
    private val apiKey: String,
    private val baseUrl: String = DEFAULT_NEAREST_URL,
    /** AFDC `country` query: `US`, `CA`, or `all` (US+CA). Default `all` for North America. */
    private val country: String = "all",
    private val cacheTtlMs: Long = 120_000L,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var cache: CachedNearest? = null

    /**
     * Public EV stations near [latitude]/[longitude] within [radiusKm], up to [limit].
     */
    suspend fun getAvailability(
        latitude: Double,
        longitude: Double,
        radiusKm: Int = 15,
        limit: Int = 200,
    ): List<AfdcPdcRecord> {
        if (apiKey.isBlank()) return emptyList()
        val stations = getOrFetchNearest(latitude, longitude, radiusKm, limit)
        return filterAvailability(
            stations = stations,
            latitude = latitude,
            longitude = longitude,
            radiusKm = radiusKm,
            limit = limit,
        )
    }

    internal fun filterAvailability(
        stations: List<AfdcStation>,
        latitude: Double,
        longitude: Double,
        radiusKm: Int,
        limit: Int,
    ): List<AfdcPdcRecord> {
        if (stations.isEmpty()) return emptyList()
        return stations.asSequence()
            .mapNotNull { station ->
                val lat = station.latitude ?: return@mapNotNull null
                val lon = station.longitude ?: return@mapNotNull null
                val id = station.id?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                // AFDC nearest returns `distance` in miles; haversine fallback is km.
                val distKm = station.distance?.let { it * MILES_TO_KM }
                    ?: haversineKm(latitude, longitude, lat, lon)
                if (distKm > radiusKm) return@mapNotNull null
                val statusRaw = station.statusCode?.trim().orEmpty()
                if (isRemovedOrSkip(statusRaw)) return@mapNotNull null
                val address = listOfNotNull(
                    station.streetAddress,
                    station.city,
                    station.state,
                    station.country,
                ).joinToString(", ").ifBlank { null }
                AfdcPdcRecord(
                    id = id,
                    statusRaw = statusRaw.ifBlank { "UNKNOWN" },
                    latitude = lat,
                    longitude = lon,
                    stationId = id,
                    address = address ?: station.stationName,
                    distanceKm = distKm,
                )
            }
            .sortedBy { it.distanceKm }
            .take(limit)
            .toList()
    }

    internal fun parseNearestJson(text: String): List<AfdcStation> {
        if (text.isBlank()) return emptyList()
        return json.decodeFromString<AfdcNearestResponse>(text).fuelStations.orEmpty()
    }

    /**
     * AFDC `status_code`: E = available, T = temporarily unavailable, P = planned.
     */
    internal fun mapStatus(statusRaw: String?): AvailabilityStatus {
        val s = statusRaw?.trim()?.uppercase().orEmpty()
        return when (s) {
            "E" -> AvailabilityStatus.Available
            "T" -> AvailabilityStatus.Maintenance
            "P" -> AvailabilityStatus.PlannedIntoService
            "", "UNKNOWN" -> AvailabilityStatus.Unknown
            else -> AvailabilityStatus.Unknown
        }
    }

    private fun isRemovedOrSkip(statusRaw: String): Boolean =
        statusRaw.equals("REMOVED", ignoreCase = true)

    private suspend fun getOrFetchNearest(
        latitude: Double,
        longitude: Double,
        radiusKm: Int,
        limit: Int,
    ): List<AfdcStation> = mutex.withLock {
        val cached = cache
        val now = nowMs()
        val sameQuery = cached != null &&
            haversineKm(cached.lat, cached.lon, latitude, longitude) < 0.5 &&
            cached.radiusKm == radiusKm
        if (sameQuery && now - cached!!.atMs < cacheTtlMs) return@withLock cached.stations

        val radiusMiles = (radiusKm.coerceIn(1, 500) / MILES_TO_KM).coerceAtLeast(1.0)
        val response = client.get(baseUrl) {
            parameter("api_key", apiKey)
            parameter("fuel_type", "ELEC")
            parameter("latitude", latitude)
            parameter("longitude", longitude)
            parameter("radius", radiusMiles)
            parameter("limit", limit.coerceIn(1, 200))
            parameter("access", "public")
            parameter("status", "E,T,P")
            parameter("country", country)
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw NetworkException(response.status.value, "AFDC nearest error: $body")
        }
        val stations = parseNearestJson(body)
        cache = CachedNearest(stations, latitude, longitude, radiusKm, now)
        stations
    }

    private data class CachedNearest(
        val stations: List<AfdcStation>,
        val lat: Double,
        val lon: Double,
        val radiusKm: Int,
        val atMs: Long,
    )

    companion object {
        /** Prefer nlr.gov host (nrel.gov DNS may fail in some environments). */
        const val DEFAULT_NEAREST_URL =
            "https://developer.nlr.gov/api/alt-fuel-stations/v1/nearest.json"

        private const val MILES_TO_KM = 1.609344
    }
}

data class AfdcPdcRecord(
    val id: String,
    val statusRaw: String,
    val latitude: Double,
    val longitude: Double,
    val stationId: String?,
    val address: String?,
    val distanceKm: Double,
)

@Serializable
internal data class AfdcNearestResponse(
    @SerialName("fuel_stations") val fuelStations: List<AfdcStation>? = null,
)

@Serializable
internal data class AfdcStation(
    val id: Long? = null,
    @SerialName("station_name") val stationName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("status_code") val statusCode: String? = null,
    @SerialName("access_code") val accessCode: String? = null,
    @SerialName("street_address") val streetAddress: String? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    /** Miles from query point when returned by nearest API. */
    val distance: Double? = null,
)
