package fr.geoking.gaston.api.australia

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.util.encodeBase64
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Serializable
data class NSWResponse(
    val stations: List<NSWStation>,
    val prices: List<NSWPrice>
)

@Serializable
data class NSWStation(
    val brand: String,
    val code: String,
    val name: String,
    val address: String,
    val location: NSWLocation
)

@Serializable
data class NSWLocation(
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class NSWPrice(
    val stationcode: String,
    val fueltype: String,
    val price: Double
)

@Serializable
data class NSWAuthResponse(
    val access_token: String
)

class AustraliaNswFuelCheckClient(private val client: HttpClient) {
    private val authUrl = "https://api.onegov.nsw.gov.au/oauth/client_credential/accesstoken?grant_type=client_credentials"
    private val pricesUrl = "https://api.onegov.nsw.gov.au/FuelPriceCheck/v1/fuel/prices"

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun fetchAllData(apiKey: String, apiSecret: String): NSWResponse {
        val basic = Base64.Default.encode("$apiKey:$apiSecret".encodeToByteArray())
        val authRes = client.get(authUrl) {
            header("Authorization", "Basic $basic")
        }
        val token = authRes.body<NSWAuthResponse>().access_token

        val response = client.get(pricesUrl) {
            header("Authorization", "Bearer $token")
            header("apikey", apiKey)
            header("Content-Type", "application/json")
        }
        return response.body<NSWResponse>()
    }
}
