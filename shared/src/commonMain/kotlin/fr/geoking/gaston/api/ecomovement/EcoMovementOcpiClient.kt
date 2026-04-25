package fr.geoking.gaston.api.ecomovement

import fr.geoking.gaston.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Minimal client for Eco-Movement OCPI Data API (CPO 2.2).
 *
 * Base URL (per Eco-Movement docs): https://api.eco-movement.com/api/ocpi/cpo/2.2
 * Auth: `Authorization: Token <apiKey>`
 */
class EcoMovementOcpiClient(
    private val client: HttpClient,
    private val apiKey: String,
    private val baseUrl: String = "https://api.eco-movement.com/api/ocpi/cpo/2.2"
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getVersions(): List<EcoMovementOcpiVersion> =
        getOcpi("/versions")

    suspend fun listLocations(limit: Int = 100, offset: Int = 0): List<EcoMovementOcpiLocation> {
        val safeLimit = limit.coerceIn(1, 1000)
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
        val response = client.get(url) {
            header(HttpHeaders.Authorization, "Token $apiKey")
            header(HttpHeaders.Accept, "application/json")
        }
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "Eco-Movement OCPI error: $body")
        }
        val envelope = json.decodeFromString<EcoMovementOcpiEnvelope<T>>(body)
        if (envelope.statusCode != 1000) {
            throw NetworkException(
                response.status.value,
                "Eco-Movement OCPI status ${envelope.statusCode}: ${envelope.statusMessage ?: "Unknown"}"
            )
        }
        return envelope.data
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
    val postal_code: String? = null,
    val country: String? = null,
    val coordinates: EcoMovementOcpiCoordinates? = null,
    val evses: List<EcoMovementOcpiEvse>? = null,
    val operator: EcoMovementOcpiOperator? = null
)

@Serializable
data class EcoMovementOcpiEvse(
    val uid: String? = null,
    val evse_id: String? = null,
    val status: String? = null,
    val connectors: List<EcoMovementOcpiConnector>? = null
)

@Serializable
data class EcoMovementOcpiConnector(
    val id: String? = null,
    val standard: String? = null,
    val format: String? = null,
    val power_type: String? = null,
    val max_voltage: Int? = null,
    val max_amperage: Int? = null,
    val max_electric_power: Int? = null
)

@Serializable
data class EcoMovementOcpiCoordinates(
    val latitude: String? = null,
    val longitude: String? = null
)

@Serializable
data class EcoMovementOcpiOperator(
    val name: String? = null
)

