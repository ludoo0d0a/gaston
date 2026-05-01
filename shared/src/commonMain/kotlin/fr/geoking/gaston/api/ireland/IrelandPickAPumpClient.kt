package fr.geoking.gaston.api.ireland

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.serialization.Serializable

@Serializable
data class PAPStation(
    val id: String,
    val stationName: String,
    val brand: String? = null,
    val address: String? = null,
    val town: String? = null,
    val county: String? = null,
    val country: String, // ROI, NI, UK
    val coords: PAPCoords,
    val prices: PAPPrices? = null
)

@Serializable
data class PAPCoords(
    val lat: Double,
    val lng: Double
)

@Serializable
data class PAPPrices(
    val petrol: Double? = null,
    val diesel: Double? = null,
    val petrolplus: Double? = null,
    val dieselplus: Double? = null,
    val hvo: Double? = null
)

class IrelandPickAPumpClient(private val client: HttpClient) {
    private val baseUrl = "https://api.pickapump.com/v1/stations/nearby"

    suspend fun fetchNearbyStations(lat: Double, lon: Double, radius: Int): List<PAPStation> {
        val response = client.get("$baseUrl?lat=$lat&lng=$lon&radius=$radius") {
            header("Accept", "application/json")
            header("User-Agent", "Mozilla/5.0 (compatible; Julius/1.0)")
            header("Origin", "https://pickapump.com")
        }
        return response.body<List<PAPStation>>()
    }
}
