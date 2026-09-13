package fr.geoking.gaston.api.freshmile

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * API Client for Freshmile EV charging network (`https://prod-driver-api.freshmile.com/charge/api/v2`).
 */
class FreshmileClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = "https://prod-driver-api.freshmile.com/charge/api/v2",
    private val cacheMaxAgeMs: Long = 5 * 60_000L
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val mutex = Mutex()
    private var cachedBbox: String? = null
    private var cachedResponse: FreshmileMapLocationsResponseDto? = null
    private var cacheTimestampMs: Long = 0L

    /**
     * Fetches charging station map locations within the given bounding box and zoom level.
     * Bbox format: minLng,minLat,maxLng,maxLat
     */
    suspend fun getMapLocations(
        minLng: Double,
        minLat: Double,
        maxLng: Double,
        maxLat: Double,
        zoom: Int = 14
    ): FreshmileMapLocationsResponseDto = mutex.withLock {
        val bboxStr = "$minLng,$minLat,$maxLng,$maxLat"
        val now = currentTimeMs()

        if (cachedBbox == bboxStr && cachedResponse != null && (now - cacheTimestampMs) < cacheMaxAgeMs) {
            return cachedResponse!!
        }

        val responseText = httpClient.get("$baseUrl/map-locations") {
            header("Accept", "application/json")
            parameter("zoom", zoom)
            url {
                parameters.append("bbox[]", minLng.toString())
                parameters.append("bbox[]", minLat.toString())
                parameters.append("bbox[]", maxLng.toString())
                parameters.append("bbox[]", maxLat.toString())
            }
        }.bodyAsText()

        val response = json.decodeFromString<FreshmileMapLocationsResponseDto>(responseText)

        cachedBbox = bboxStr
        cachedResponse = response
        cacheTimestampMs = now

        response
    }

    /**
     * Fetches full location details for a single station by its ID.
     */
    suspend fun getLocationDetail(locationId: Long): FreshmileLocationDetailDto? {
        return try {
            val url = "$baseUrl/locations/$locationId"
            val responseText = httpClient.get(url) {
                header("Accept", "application/json")
            }.bodyAsText()

            val decoded = json.decodeFromString<FreshmileLocationDetailResponseDto>(responseText)
            decoded.data
        } catch (_: Exception) {
            null
        }
    }

    suspend fun clearCache() = mutex.withLock {
        cachedBbox = null
        cachedResponse = null
        cacheTimestampMs = 0L
    }
}

@Serializable
data class FreshmileMapLocationsResponseDto(
    val type: String? = null,
    val bbox: List<Double>? = null,
    val features: List<FreshmileFeatureDto>? = null
)

@Serializable
data class FreshmileFeatureDto(
    val type: String? = null,
    val id: String? = null,
    val geometry: FreshmileGeometryDto? = null,
    val properties: FreshmilePropertiesDto? = null
)

@Serializable
data class FreshmileGeometryDto(
    val type: String? = null,
    val coordinates: List<Double>? = null
)

@Serializable
data class FreshmilePropertiesDto(
    @SerialName("location_id") val locationId: Long? = null,
    @SerialName("location_count") val locationCount: Int? = null,
    @SerialName("best_power_category") val bestPowerCategory: String? = null,
    @SerialName("is_available") val isAvailable: Boolean? = null,
    @SerialName("is_open") val isOpen: Boolean? = null,
    @SerialName("total_evses") val totalEvses: Int? = null,
    val status: Int? = null,
    val hash: String? = null
)

@Serializable
data class FreshmileLocationDetailResponseDto(
    val data: FreshmileLocationDetailDto? = null
)

@Serializable
data class FreshmileLocationDetailDto(
    val id: Long? = null,
    val ref: String? = null,
    val name: String? = null,
    @SerialName("is_available") val isAvailable: Boolean? = null,
    @SerialName("is_open") val isOpen: Boolean? = null,
    @SerialName("has_free_tariff") val hasFreeTariff: Boolean? = null,
    val coordinates: FreshmileCoordinatesDto? = null,
    val address: FreshmileAddressDto? = null,
    val evses: List<FreshmileEvseDto>? = null,
    val connectors: FreshmileConnectorsSummaryDto? = null
)

@Serializable
data class FreshmileCoordinatesDto(
    val latitude: Double? = null,
    val longitude: Double? = null
)

@Serializable
data class FreshmileAddressDto(
    val fullname: String? = null,
    val city: String? = null,
    @SerialName("postal_code") val postalCode: String? = null,
    val country: String? = null
)

@Serializable
data class FreshmileConnectorsSummaryDto(
    @SerialName("best_power") val bestPower: FreshmileBestPowerDto? = null,
    val types: List<String>? = null
)

@Serializable
data class FreshmileBestPowerDto(
    val category: String? = null,
    val kw: Double? = null
)

@Serializable
data class FreshmileEvseDto(
    val id: Long? = null,
    @SerialName("custom_ref") val customRef: String? = null,
    val status: String? = null,
    @SerialName("is_available") val isAvailable: Boolean? = null,
    @SerialName("location_id") val locationId: Long? = null,
    val connectors: List<FreshmileConnectorDto>? = null
)

@Serializable
data class FreshmileConnectorDto(
    val id: Long? = null,
    val power: Double? = null,
    val standard: String? = null,
    val tariff: FreshmileTariffDto? = null
)

@Serializable
data class FreshmileTariffDto(
    val id: Long? = null,
    val name: String? = null,
    val description: String? = null,
    @SerialName("is_free") val isFree: Boolean? = null,
    val currency: String? = null
)

private fun currentTimeMs(): Long = System.currentTimeMillis()
