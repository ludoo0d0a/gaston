package fr.geoking.gaston.api.australia

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class FuelWatchProduct(
    val shortName: String,
)

@Serializable
data class FuelWatchStation(
    val id: Int,
    val siteName: String,
    val brandName: String? = null,
    val productFuelType: String,
    val address: FuelWatchAddress,
    val product: FuelWatchProductPrice? = null,
)

@Serializable
data class FuelWatchAddress(
    val line1: String? = null,
    val postCode: String? = null,
    val latitude: Double,
    val longitude: Double,
)

@Serializable
data class FuelWatchProductPrice(
    val priceToday: Double? = null,
)

class FuelWatchClient(private val client: HttpClient) {
    private val baseUrl = "https://www.fuelwatch.wa.gov.au/api"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchProductTypes(): List<String> {
        val body = client.get("$baseUrl/products") {
            header("Accept", "application/json")
            header("User-Agent", DESKTOP_USER_AGENT)
        }.bodyAsText()
        return json.decodeFromString<List<FuelWatchProduct>>(body).map { it.shortName }
    }

    suspend fun fetchStationsForProduct(fuelType: String): List<FuelWatchStation> {
        val body = client.get("$baseUrl/sites?fuelType=$fuelType") {
            header("Accept", "application/json")
            header("User-Agent", DESKTOP_USER_AGENT)
        }.bodyAsText()
        return json.decodeFromString(body)
    }

    private companion object {
        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64; rv:120.0) Gecko/20100101 Firefox/120.0"
    }
}
