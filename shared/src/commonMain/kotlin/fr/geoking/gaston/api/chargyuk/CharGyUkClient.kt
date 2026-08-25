package fr.geoking.gaston.api.chargyuk

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
 * Open OCPI client for char.gy (UK on-street EV charging network).
 * Base URL: https://char.gy/open-ocpi
 * No authentication / API key required.
 */
class CharGyUkClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://char.gy/open-ocpi"
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getLocations(limit: Int = 100, offset: Int = 0): List<CharGyUkLocation> {
        val url = "${baseUrl.trimEnd('/')}/locations?limit=${limit.coerceIn(1, 500)}&offset=${offset.coerceAtLeast(0)}"
        val response = client.get(url) {
            header(HttpHeaders.Accept, "application/json")
        }
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "char.gy UK OCPI error: $body")
        }
        val envelope = json.decodeFromString<CharGyUkEnvelope<List<CharGyUkLocation>>>(body)
        if (envelope.statusCode != 1000) {
            throw NetworkException(
                response.status.value,
                "char.gy UK OCPI status ${envelope.statusCode}: ${envelope.statusMessage ?: "Unknown"}"
            )
        }
        return envelope.data
    }
}

@Serializable
data class CharGyUkEnvelope<T>(
    @SerialName("data") val data: T,
    @SerialName("status_code") val statusCode: Int,
    @SerialName("status_message") val statusMessage: String? = null
)

@Serializable
data class CharGyUkLocation(
    val id: String,
    val name: String? = null,
    val address: String? = null,
    val city: String? = null,
    @SerialName("postal_code") val postalCode: String? = null,
    val coordinates: CharGyUkCoordinates? = null,
    val evses: List<CharGyUkEvse>? = null
)

@Serializable
data class CharGyUkCoordinates(
    val latitude: String? = null,
    val longitude: String? = null
)

@Serializable
data class CharGyUkEvse(
    val uid: String? = null,
    val status: String? = null,
    val connectors: List<CharGyUkConnector>? = null
)

@Serializable
data class CharGyUkConnector(
    val id: String? = null,
    val standard: String? = null,
    val format: String? = null,
    @SerialName("power_type") val powerType: String? = null,
    @SerialName("max_electric_power") val maxElectricPower: Double? = null
)
