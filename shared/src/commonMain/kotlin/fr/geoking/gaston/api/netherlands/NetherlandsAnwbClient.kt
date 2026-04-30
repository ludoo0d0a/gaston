package fr.geoking.gaston.api.netherlands

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.serialization.Serializable

@Serializable
data class ANWBResponse(
    val value: List<ANWBStation>
)

@Serializable
data class ANWBStation(
    val id: String,
    val title: String,
    val coordinates: ANWBCoordinates,
    val address: ANWBAddress? = null,
    val prices: List<ANWBPrice>? = null
)

@Serializable
data class ANWBCoordinates(
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class ANWBAddress(
    val streetAddress: String? = null,
    val postalCode: String? = null,
    val city: String? = null,
    val country: String? = null,
    val iso3CountryCode: String? = null
)

@Serializable
data class ANWBPrice(
    val fuelType: String,
    val value: Double,
    val currency: String
)

class NetherlandsAnwbClient(private val client: HttpClient) {
    private val baseUrl = "https://api.anwb.nl/routing/points-of-interest/v3/all"

    suspend fun fetchStations(bbox: String): List<ANWBStation> {
        val url = "$baseUrl?type-filter=FUEL_STATION&bounding-box-filter=$bbox"
        val response = client.get(url) {
            header("Accept", "application/json")
            header("User-Agent", "Gaston/1.0")
        }
        return response.body<ANWBResponse>().value
    }
}
