package fr.geoking.gaston.api.croatia

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.serialization.Serializable

@Serializable
data class MZOEData(
    val postajas: List<MZOEStation>,
    val gorivos: List<MZOEGorivo>,
    val obvezniks: List<MZOEObveznik>
)

@Serializable
data class MZOEStation(
    val id: Int,
    val naziv: String,
    val adresa: String,
    val mjesto: String,
    val lat: Double, // Longitude (API bug)
    val long: Double, // Latitude (API bug)
    val obveznik_id: Int,
    val cjenici: List<MZOECjenik>
)

@Serializable
data class MZOEGorivo(
    val id: Int,
    val naziv: String,
    val vrsta_goriva_id: Int? = null
)

@Serializable
data class MZOEObveznik(
    val id: Int,
    val naziv: String
)

@Serializable
data class MZOECjenik(
    val cijena: Double,
    val gorivo_id: Int
)

class CroatiaMzoeClient(private val client: HttpClient) {
    private val dataUrl = "https://mzoe-gor.hr/data.json"

    suspend fun fetchData(): MZOEData {
        val response = client.get(dataUrl) {
            header("Accept", "application/json")
            header("User-Agent", "Gaston/1.0")
        }
        return response.body<MZOEData>()
    }
}
