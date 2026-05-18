package fr.geoking.gaston.api.romania

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.serialization.Serializable

@Serializable
data class ParseResponse(
    val results: List<PecoStation>,
    val count: Int? = null
)

@Serializable
data class PecoStation(
    val objectId: String,
    val Id: String? = null,
    val Retea: String? = null,
    val Statie: String? = null,
    val Adresa: String? = null,
    val Oras: String? = null,
    val Judet: String? = null,
    val lat: Double,
    val lng: Double,
    val Benzina_Regular: Double? = null,
    val Benzina_Premium: Double? = null,
    val Motorina_Regular: Double? = null,
    val Motorina_Premium: Double? = null,
    val GPL: Double? = null,
    val AdBlue: Double? = null
)

class RomaniaPecoClient(
    private val client: HttpClient,
    private val applicationId: String,
    private val clientKey: String,
) {
    private val apiUrl = "https://pg-app-hnf14cfy2xb2v9x9eueuchcd2xyetd.scalabl.cloud/1/classes/farapret3"

    private fun parseHeaders(): Map<String, String>? {
        if (applicationId.isBlank() || clientKey.isBlank()) return null
        return mapOf(
            "X-Parse-Application-Id" to applicationId,
            "X-Parse-Client-Key" to clientKey,
            "User-Agent" to "Parse Android SDK API Level 34",
        )
    }

    suspend fun fetchAllStations(): List<PecoStation> {
        val headers = parseHeaders() ?: return emptyList()
        val stations = mutableListOf<PecoStation>()
        val limit = 1000
        var skip = 0

        val where = "{\"Benzina_Regular\":{\"\$gt\":0,\"\$lt\":999999}}"

        while (true) {
            val url = "$apiUrl?limit=$limit&skip=$skip&where=$where"
            val response = client.get(url) {
                headers.forEach { (k, v) -> header(k, v) }
                header("Accept", "application/json")
            }
            val data = response.body<ParseResponse>()
            stations.addAll(data.results)
            if (data.results.size < limit) break
            skip += data.results.size
        }
        return stations
    }
}
