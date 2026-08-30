package fr.geoking.gaston.api.dkv

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
 * Lightweight OCPI client for DKV Mobility (Azure API Management).
 *
 * API host: `https://api.dkv-mobility.com` (developer docs live on `api-portal.dkv-mobility.com`).
 * Auth typically requires an APIM subscription key header:
 *   - `Ocp-Apim-Subscription-Key: <key>`
 *
 * Some deployments also require OAuth2 Bearer or an OCPI credentials token via:
 *   - `Authorization: Bearer <token>` or `Authorization: Token <token>`
 *
 * This client supports both: [subscriptionKey] is mandatory, [authorization] is optional.
 */
class DkvOcpiClient(
    private val client: HttpClient,
    private val subscriptionKey: String,
    private val authorization: String? = null,
    private val baseUrl: String = "https://api.dkv-mobility.com/ocpi/cpo/2.2.1"
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun listLocations(limit: Int = 200, offset: Int = 0): List<DkvOcpiLocation> {
        val url = "${baseUrl.trimEnd('/')}/locations?limit=${limit.coerceIn(1, 50)}&offset=${offset.coerceAtLeast(0)}"
        return getOcpi(url)
    }

    suspend fun listTariffs(limit: Int = 100, offset: Int = 0): List<DkvOcpiTariff> {
        val url = "${baseUrl.trimEnd('/')}/tariffs?limit=${limit.coerceIn(1, 1000)}&offset=${offset.coerceAtLeast(0)}"
        return getOcpi(url)
    }

    private suspend inline fun <reified T> getOcpi(url: String): T {
        val response = client.get(url) {
            header("Ocp-Apim-Subscription-Key", subscriptionKey)
            authorization?.takeIf { it.isNotBlank() }?.let { header(HttpHeaders.Authorization, it) }
            header(HttpHeaders.Accept, "application/json")
        }
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "DKV OCPI error")
        }
        val envelope = json.decodeFromString<DkvOcpiEnvelope<T>>(body)
        if (envelope.statusCode != 1000) {
            throw NetworkException(
                response.status.value,
                "DKV OCPI status ${envelope.statusCode}: ${envelope.statusMessage ?: "Unknown"}"
            )
        }
        return envelope.data
    }
}

// ── OCPI envelope ────────────────────────────────────────────────────────────

@Serializable
data class DkvOcpiEnvelope<T>(
    @SerialName("data") val data: T,
    @SerialName("status_code") val statusCode: Int,
    @SerialName("status_message") val statusMessage: String? = null,
    @SerialName("timestamp") val timestamp: String? = null
)

// ── OCPI Location (subset) ───────────────────────────────────────────────────

@Serializable
data class DkvOcpiLocation(
    val id: String,
    val name: String? = null,
    val address: String? = null,
    val city: String? = null,
    @SerialName("postal_code") val postalCode: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    val coordinates: DkvOcpiCoordinates? = null,
    val evses: List<DkvOcpiEvse>? = null,
    val operator: DkvOcpiBusinessDetails? = null
)

@Serializable
data class DkvOcpiCoordinates(
    val latitude: String? = null,
    val longitude: String? = null
)

@Serializable
data class DkvOcpiBusinessDetails(
    val name: String? = null,
    val website: String? = null
)

@Serializable
data class DkvOcpiEvse(
    val uid: String? = null,
    @SerialName("evse_id") val evseId: String? = null,
    val status: String? = null,
    val connectors: List<DkvOcpiConnector>? = null
)

@Serializable
data class DkvOcpiConnector(
    val id: String? = null,
    val standard: String? = null,
    val format: String? = null,
    @SerialName("power_type") val powerType: String? = null,
    @SerialName("max_voltage") val maxVoltage: Int? = null,
    @SerialName("max_amperage") val maxAmperage: Int? = null,
    /** Watts. */
    @SerialName("max_electric_power") val maxElectricPower: Int? = null
)

// ── OCPI Tariff (subset) ─────────────────────────────────────────────────────

@Serializable
data class DkvOcpiTariff(
    val id: String,
    val currency: String? = null,
    val elements: List<DkvOcpiTariffElement>? = null
)

@Serializable
data class DkvOcpiTariffElement(
    @SerialName("price_components") val priceComponents: List<DkvOcpiPriceComponent>? = null
)

@Serializable
data class DkvOcpiPriceComponent(
    val type: String? = null,
    val price: Double? = null,
    val vat: Double? = null,
    @SerialName("step_size") val stepSize: Int? = null
)

