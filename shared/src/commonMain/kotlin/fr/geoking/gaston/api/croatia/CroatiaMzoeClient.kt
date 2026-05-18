package fr.geoking.gaston.api.croatia

import fr.geoking.gaston.api.common.StringOrDoubleSerializer
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class MZOEData(
    val postajas: List<MZOEStation>,
    val gorivos: List<MZOEGorivo>,
    val obvezniks: List<MZOEObveznik>
)

@Serializable
data class MZOEStation(
    val id: Int,
    val naziv: String? = null,
    val adresa: String? = null,
    val mjesto: String? = null,
    @Serializable(with = StringOrDoubleSerializer::class)
    val lat: Double, // Longitude (API bug)
    @Serializable(with = StringOrDoubleSerializer::class)
    val long: Double, // Latitude (API bug)
    val obveznik_id: Int,
    val cjenici: List<MZOECjenik>
)

@Serializable
data class MZOEGorivo(
    val id: Int,
    val naziv: String? = null,
    val vrsta_goriva_id: Int? = null
)

@Serializable
data class MZOEObveznik(
    val id: Int,
    val naziv: String? = null
)

@Serializable
data class MZOECjenik(
    val cijena: Double,
    val gorivo_id: Int
)

class CroatiaMzoeClient(private val client: HttpClient) {
    private val dataUrl = "https://mzoe-gor.hr/data.json"
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun fetchData(): MZOEData {
        val text = client.get(dataUrl) {
            header("Accept", "application/json")
            header("User-Agent", "Gaston/1.0")
        }.bodyAsText()
        return json.decodeFromString(text)
    }
}
