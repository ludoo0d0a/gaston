package fr.geoking.julius.api.ocpi

import fr.geoking.julius.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json

/**
 * Generic OCPI client for fetching locations and status.
 * Supports Token-based authentication as required by OCPI 2.2.1.
 */
class OcpiClient(
    private val client: HttpClient,
    private val baseUrl: String,
    private val token: String,
    private val authHeaderName: String = "Authorization",
    private val useTokenPrefix: Boolean = true
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun getAuthHeaderValue(): String? {
        if (token.isBlank()) return null
        return if (useTokenPrefix) {
            if (token.startsWith("Token ", ignoreCase = true)) {
                "Token " + token.substring(6)
            } else {
                "Token $token"
            }
        } else {
            token
        }
    }

    /**
     * Fetches locations from the OCPI /locations endpoint.
     * Optional bounding box or radius filtering depends on CPO implementation.
     */
    suspend fun getLocations(
        latitude: Double? = null,
        longitude: Double? = null,
        radiusKm: Int? = null
    ): List<OcpiLocation> {
        if (baseUrl.isBlank()) return emptyList()

        // Ensure we don't duplicate /locations if it's already in baseUrl
        val url = if (baseUrl.endsWith("/locations")) baseUrl else "$baseUrl/locations"

        val response = client.get(url) {
            getAuthHeaderValue()?.let { header(authHeaderName, it) }
            // OCPI /locations typically supports date_from, date_to for delta.
            // Some implementations support lat/lon/radius as query params (non-standard extension).
            if (latitude != null) parameter("latitude", latitude)
            if (longitude != null) parameter("longitude", longitude)
            if (radiusKm != null) parameter("radius", radiusKm)
        }

        // Gracefully handle 404 (misconfigured endpoint or provider changed its URL structure)
        if (response.status == HttpStatusCode.NotFound) {
            return emptyList()
        }

        if (response.status.value != 200) {
            throw NetworkException(
                httpCode = response.status.value,
                message = "OCPI Error: ${response.bodyAsText()}",
                url = url,
                provider = "OCPI"
            )
        }

        val ocpiResponse: OcpiResponse<List<OcpiLocation>> = response.body()
        return ocpiResponse.data ?: emptyList()
    }

    /**
     * Fetches tariffs from the OCPI /tariffs endpoint.
     */
    suspend fun getTariffs(): List<OcpiTariff> {
        if (baseUrl.isBlank()) return emptyList()

        val url = if (baseUrl.endsWith("/tariffs")) baseUrl else "$baseUrl/tariffs"

        val response = client.get(url) {
            getAuthHeaderValue()?.let { header(authHeaderName, it) }
        }

        if (response.status == HttpStatusCode.NotFound) {
            return emptyList()
        }

        if (response.status.value != 200) {
            throw NetworkException(
                httpCode = response.status.value,
                message = "OCPI Error: ${response.bodyAsText()}",
                url = url,
                provider = "OCPI"
            )
        }

        val ocpiResponse: OcpiResponse<List<OcpiTariff>> = response.body()
        return ocpiResponse.data ?: emptyList()
    }

    /**
     * Fetches a specific tariff by ID from the OCPI /tariffs endpoint.
     */
    suspend fun getTariff(tariffId: String): OcpiTariff? {
        if (baseUrl.isBlank()) return null

        val baseUrlWithoutTrailing = baseUrl.removeSuffix("/")
        val url = "$baseUrlWithoutTrailing/tariffs/$tariffId"

        val response = client.get(url) {
            getAuthHeaderValue()?.let { header(authHeaderName, it) }
        }

        if (response.status == HttpStatusCode.NotFound) {
            return null
        }

        if (response.status.value != 200) {
            return null // Silently fail for individual tariff fetch
        }

        val ocpiResponse: OcpiResponse<OcpiTariff> = response.body()
        return ocpiResponse.data
    }
}
