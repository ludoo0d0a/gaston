package fr.geoking.gaston.api.fastned

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
 * OCPI 2.2.1 client for the Fastned UK Open Data API.
 *
 * Endpoints:
 *   GET /locations  – OCPI Location objects (charging stations)
 *   GET /tariffs    – OCPI Tariff objects (pricing)
 *
 * Auth: `x-api-key` request header (not Bearer / Token).
 * Spec: https://evroaming.org/app/uploads/2021/11/OCPI-2.2.1d2.pdf
 */
class FastnedOcpiClient(
    private val client: HttpClient,
    private val apiKey: String,
    private val baseUrl: String = "https://uk-public.api.fastned.nl/uk-public/ocpi/cpo/2.2.1"
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fetches a single page of OCPI Location objects.
     * OCPI pagination: pass [offset] / [limit] as query params.
     */
    suspend fun listLocations(limit: Int = 200, offset: Int = 0): List<FastnedOcpiLocation> {
        val url = "${baseUrl.trimEnd('/')}/locations?limit=${limit.coerceIn(1, 1000)}&offset=${offset.coerceAtLeast(0)}"
        return getOcpi(url)
    }

    /**
     * Fetches a single page of OCPI Tariff objects.
     */
    suspend fun listTariffs(limit: Int = 100, offset: Int = 0): List<FastnedOcpiTariff> {
        val url = "${baseUrl.trimEnd('/')}/tariffs?limit=${limit.coerceIn(1, 1000)}&offset=${offset.coerceAtLeast(0)}"
        return getOcpi(url)
    }

    private suspend inline fun <reified T> getOcpi(url: String): T {
        val response = client.get(url) {
            header("x-api-key", apiKey)
            header(HttpHeaders.Accept, "application/json")
        }
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "Fastned OCPI error: $body")
        }
        val envelope = json.decodeFromString<FastnedOcpiEnvelope<T>>(body)
        if (envelope.statusCode != 1000) {
            throw NetworkException(
                response.status.value,
                "Fastned OCPI status ${envelope.statusCode}: ${envelope.statusMessage ?: "Unknown"}"
            )
        }
        return envelope.data
    }
}

// ── OCPI 2.2.1 envelope ──────────────────────────────────────────────────────

@Serializable
data class FastnedOcpiEnvelope<T>(
    @SerialName("data") val data: T,
    @SerialName("status_code") val statusCode: Int,
    @SerialName("status_message") val statusMessage: String? = null,
    @SerialName("timestamp") val timestamp: String? = null
)

// ── OCPI 2.2.1 Location ──────────────────────────────────────────────────────

@Serializable
data class FastnedOcpiLocation(
    val id: String,
    val name: String? = null,
    val address: String? = null,
    val city: String? = null,
    @SerialName("postal_code") val postalCode: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    val country: String? = null,
    val coordinates: FastnedOcpiCoordinates? = null,
    val evses: List<FastnedOcpiEvse>? = null,
    val operator: FastnedOcpiBusinessDetails? = null,
    val suboperator: FastnedOcpiBusinessDetails? = null,
    val owner: FastnedOcpiBusinessDetails? = null
)

@Serializable
data class FastnedOcpiCoordinates(
    val latitude: String? = null,
    val longitude: String? = null
)

@Serializable
data class FastnedOcpiBusinessDetails(
    val name: String? = null,
    val website: String? = null
)

// ── OCPI 2.2.1 EVSE / Connector ──────────────────────────────────────────────

@Serializable
data class FastnedOcpiEvse(
    val uid: String? = null,
    @SerialName("evse_id") val evseId: String? = null,
    val status: String? = null,
    val connectors: List<FastnedOcpiConnector>? = null
)

/**
 * OCPI 2.2.1 Connector.
 * [maxElectricPower] is in **watts**; divide by 1000 for kW.
 */
@Serializable
data class FastnedOcpiConnector(
    val id: String? = null,
    /** OCPI standard, e.g. "IEC_62196_T2", "IEC_62196_T2_COMBO", "CHADEMO". */
    val standard: String? = null,
    /** "SOCKET" or "CABLE". */
    val format: String? = null,
    @SerialName("power_type") val powerType: String? = null,
    @SerialName("max_voltage") val maxVoltage: Int? = null,
    @SerialName("max_amperage") val maxAmperage: Int? = null,
    /** Watts. */
    @SerialName("max_electric_power") val maxElectricPower: Int? = null
)

// ── OCPI 2.2.1 Tariff ────────────────────────────────────────────────────────

@Serializable
data class FastnedOcpiTariff(
    val id: String,
    val currency: String? = null,
    @SerialName("tariff_alt_text") val tariffAltText: List<FastnedOcpiDisplayText>? = null,
    val elements: List<FastnedOcpiTariffElement>? = null
)

@Serializable
data class FastnedOcpiDisplayText(
    val language: String? = null,
    val text: String? = null
)

@Serializable
data class FastnedOcpiTariffElement(
    @SerialName("price_components") val priceComponents: List<FastnedOcpiPriceComponent>? = null
)

@Serializable
data class FastnedOcpiPriceComponent(
    /** "ENERGY", "FLAT", "PARKING_TIME", "TIME". */
    val type: String? = null,
    val price: Double? = null,
    val vat: Double? = null,
    @SerialName("step_size") val stepSize: Int? = null
)
