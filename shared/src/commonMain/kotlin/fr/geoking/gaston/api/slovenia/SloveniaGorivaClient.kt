package fr.geoking.julius.api.slovenia

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.serialization.Serializable

@Serializable
data class GorivaSIResponse(
    val results: List<GorivaSIStation>,
    val next: String? = null
)

@Serializable
data class GorivaSIStation(
    val pk: Int,
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val prices: Map<String, Double?>
)

class SloveniaGorivaClient(private val client: HttpClient) {
    private val baseUrl = "https://goriva.si/api/v1/search/"
    private val queryCenterLat = 46.15
    private val queryCenterLng = 14.99
    private val queryRadius = 200000 // 200km

    suspend fun fetchAllStations(): List<GorivaSIStation> {
        val stations = mutableListOf<GorivaSIStation>()
        var url: String? = "$baseUrl?position=$queryCenterLat,$queryCenterLng&radius=$queryRadius"

        while (url != null) {
            val response = client.get(url) {
                header("Accept", "application/json")
                header("User-Agent", "Mozilla/5.0 (compatible; Julius/1.0)")
            }
            val data = response.body<GorivaSIResponse>()
            stations.addAll(data.results)
            url = data.next
        }
        return stations
    }
}
