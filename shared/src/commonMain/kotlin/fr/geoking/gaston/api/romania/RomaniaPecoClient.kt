package fr.geoking.gaston.api.romania

import fr.geoking.gaston.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.delay
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
    private val encodedWhere = RomaniaPecoApi.WHERE_JSON.encodeURLParameter()

    fun isConfigured(): Boolean = applicationId.isNotBlank() && clientKey.isNotBlank()

    private fun parseHeaders(): Map<String, String> = mapOf(
        "X-Parse-Application-Id" to applicationId,
        "X-Parse-Client-Key" to clientKey,
        "User-Agent" to "Parse Android SDK API Level 34",
        "Accept" to "application/json",
    )

    suspend fun fetchAllStations(): List<PecoStation> {
        if (!isConfigured()) return emptyList()

        val headers = parseHeaders()
        val stations = mutableListOf<PecoStation>()
        val limit = RomaniaPecoApi.PAGE_LIMIT
        var skip = 0

        while (true) {
            val url =
                "${RomaniaPecoApi.API_URL}?limit=$limit&skip=$skip&count=1&where=$encodedWhere"
            val response = client.get(url) {
                headers.forEach { (k, v) -> header(k, v) }
            }
            if (response.status.value != 200) {
                throw NetworkException(
                    response.status.value,
                    "Peco Online HTTP ${response.status.value}",
                )
            }
            val data = response.body<ParseResponse>()

            for (s in data.results) {
                if (s.lat == 0.0 || s.lng == 0.0) continue
                if (s.lat < RomaniaPecoApi.LAT_MIN || s.lat > RomaniaPecoApi.LAT_MAX) continue
                if (s.lng < RomaniaPecoApi.LNG_MIN || s.lng > RomaniaPecoApi.LNG_MAX) continue
                stations.add(s)
            }

            if (data.results.size < limit) break
            skip += data.results.size
            delay(200)
        }
        return stations
    }
}

internal fun Double?.isValidPecoPrice(): Boolean {
    val p = this ?: return false
    return p > 0 && p < RomaniaPecoApi.NO_DATA_SENTINEL
}
