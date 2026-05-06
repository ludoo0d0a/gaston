package fr.geoking.gaston.api.denmark

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.serialization.Serializable

@Serializable
data class DKStationWithPrices(
    val company: DKCompany,
    val station: DKStation,
    val prices: Map<String, String>
)

@Serializable
data class DKCompany(
    val id: Int,
    val company: String
)

@Serializable
data class DKStation(
    val id: Int,
    val name: String,
    val address: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)

class FuelpricesDKClient(private val client: HttpClient) {
    private val apiBase = "https://fuelprices.dk/api"

    suspend fun fetchAllStations(apiKey: String): List<DKStationWithPrices> {
        val bbox = "8.0,54.5,15.2,57.8"
        val response = client.get("$apiBase/v1/stations/all?bbox=$bbox") {
            header("Accept", "application/json")
            header("X-API-KEY", apiKey)
            header("User-Agent", "Mozilla/5.0 (compatible; Gaston/1.0)")
        }
        return response.body<List<DKStationWithPrices>>()
    }
}
