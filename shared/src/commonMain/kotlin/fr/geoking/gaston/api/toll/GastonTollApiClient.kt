package fr.geoking.gaston.api.toll

import fr.geoking.gaston.toll.TollEstimate
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Client for Gaston Worker [POST /v1/toll/estimate].
 * Requires [baseUrl] (e.g. https://gaston-api….workers.dev) and Bearer [apiKey].
 */
class GastonTollApiClient(
    private val client: HttpClient,
    private val baseUrl: String,
    private val apiKey: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank()

    suspend fun estimateToll(
        routePoints: List<Pair<Double, Double>>,
        vehicleClass: Int,
    ): TollEstimate? {
        if (!isConfigured || routePoints.size < 2) return null
        val url = baseUrl.trimEnd('/') + "/v1/toll/estimate"
        val body = TollEstimateRequest(
            points = routePoints.map { listOf(it.first, it.second) },
            vehicleClass = vehicleClass,
        )
        return runCatching {
            val response = client.post(url) {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(TollEstimateRequest.serializer(), body))
            }
            if (response.status.value !in 200..299) return@runCatching null
            val parsed = json.decodeFromString(TollEstimateResponse.serializer(), response.bodyAsText())
            TollEstimate(amountEur = parsed.amountEur, currency = parsed.currency)
        }.getOrNull()
    }
}

@Serializable
private data class TollEstimateRequest(
    val points: List<List<Double>>,
    @SerialName("vehicle_class") val vehicleClass: Int,
)

@Serializable
private data class TollEstimateResponse(
    @SerialName("amount_eur") val amountEur: Double,
    val currency: String = "EUR",
)
