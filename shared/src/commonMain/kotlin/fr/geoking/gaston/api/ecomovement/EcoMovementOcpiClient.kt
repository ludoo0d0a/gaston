package fr.geoking.gaston.api.ecomovement

import fr.geoking.gaston.shared.network.NetworkException
import fr.geoking.gaston.shared.network.RateLimitTracker
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Client for Eco-Movement OCPI Data API (CPO 2.2.1).
 *
 * Base URL: https://open-chargepoints.com/api/ocpi/cpo/2.2.1
 * Auth: `Authorization: Token <apiKey>`
 * Doc: https://developers.eco-movement.com/v2.2.1/reference/get-all-locations-pcpr-api
 */
class EcoMovementOcpiClient(
    private val client: HttpClient,
    private val apiKey: String,
    private val baseUrl: String = "https://open-chargepoints.com/api/ocpi/cpo/2.2.1"
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getVersions(): List<EcoMovementOcpiVersion> =
        getOcpi("/versions")

    suspend fun listLocations(limit: Int = 100, offset: Int = 0): List<EcoMovementOcpiLocation> {
        val safeLimit = limit.coerceIn(1, MAX_PAGE)
        val safeOffset = offset.coerceAtLeast(0)
        val url = "${baseUrl.trimEnd('/')}/locations?limit=$safeLimit&offset=$safeOffset"
        return getOcpiAbsolute(url)
    }

    suspend fun getLocation(locationId: String): EcoMovementOcpiLocation =
        getOcpi("/location/$locationId")

    suspend fun getEvse(evseId: String): EcoMovementOcpiEvse =
        getOcpi("/evse/$evseId")

    private suspend inline fun <reified T> getOcpi(path: String): T {
        val url = baseUrl.trimEnd('/') + path
        return getOcpiAbsolute(url)
    }

    private suspend inline fun <reified T> getOcpiAbsolute(url: String): T {
        if (RateLimitTracker.isRateLimited(url)) {
            val remainingSec = (RateLimitTracker.getRemainingCooldownMs(url) / 1000).coerceAtLeast(1)
            throw NetworkException(429, "Rate limit active for Eco-Movement ($remainingSec s remaining)")
        }

        val response = client.get(url) {
            header(HttpHeaders.Authorization, "Token $apiKey")
            header(HttpHeaders.Accept, "application/json")
        }
        if (response.status.value == 429) {
            val retryAfter = response.headers[HttpHeaders.RetryAfter]
            RateLimitTracker.recordRateLimit(url, retryAfter)
            throw NetworkException(429, "Eco-Movement OCPI rate limit (HTTP 429)")
        }
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "Eco-Movement OCPI error: HTTP ${response.status.value}")
        }
        val body = response.bodyAsText()
        val envelope = json.decodeFromString<EcoMovementOcpiEnvelope<T>>(body)
        if (envelope.statusCode != 1000) {
            throw NetworkException(
                response.status.value,
                "Eco-Movement OCPI status ${envelope.statusCode}: ${envelope.statusMessage ?: "Unknown"}"
            )
        }
        return envelope.data
    }

    companion object {
        /** One OCPI page of 1000 full locations is ~80MB and OOMs on 384MB heaps. */
        const val MAX_PAGE = 50
    }
}

@Serializable
data class EcoMovementOcpiEnvelope<T>(
    @SerialName("data") val data: T,
    @SerialName("status_code") val statusCode: Int,
    @SerialName("status_message") val statusMessage: String? = null,
    @SerialName("timestamp") val timestamp: String? = null
)

@Serializable
data class EcoMovementOcpiVersion(
    val version: String? = null,
    val url: String? = null
)

@Serializable
data class EcoMovementOcpiLocation(
    val id: String,
    val name: String? = null,
    val address: String? = null,
    val city: String? = null,
    @SerialName("postal_code") val postalCode: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    val country: String? = null,
    val coordinates: EcoMovementOcpiCoordinates? = null,
    val evses: List<EcoMovementOcpiEvse>? = null,
    val operator: EcoMovementOcpiBusinessDetails? = null
)

@Serializable
data class EcoMovementOcpiBusinessDetails(
    val name: String? = null,
    val website: String? = null
)

@Serializable
data class EcoMovementOcpiEvse(
    val uid: String? = null,
    @SerialName("evse_id") val evseId: String? = null,
    val status: String? = null,
    val connectors: List<EcoMovementOcpiConnector>? = null
)

@Serializable
data class EcoMovementOcpiConnector(
    val id: String? = null,
    val standard: String? = null,
    val format: String? = null,
    @SerialName("power_type") val powerType: String? = null,
    @SerialName("max_voltage") val maxVoltage: Int? = null,
    @SerialName("max_amperage") val maxAmperage: Int? = null,
    @SerialName("max_electric_power") val maxElectricPower: Int? = null
)

@Serializable
data class EcoMovementOcpiCoordinates(
    val latitude: String? = null,
    val longitude: String? = null
)
