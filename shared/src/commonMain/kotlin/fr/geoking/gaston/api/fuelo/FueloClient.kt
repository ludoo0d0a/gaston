package fr.geoking.gaston.api.fuelo

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.Parameters
import kotlinx.serialization.Serializable

@Serializable
data class FueloListResponse(
    val status: String,
    val gasstations: List<FueloStationEntry>
)

@Serializable
data class FueloStationEntry(
    val id: String? = null,
    val lat: String,
    val lon: String,
    val logo: String,
    val cluster_count: String
)

@Serializable
data class FueloInfoResponse(
    val status: String,
    val text: String
)

data class FueloStation(
    val id: String,
    val lat: Double,
    val lon: Double,
    val logo: String
)

data class FueloBounds(
    val latMin: Double,
    val latMax: Double,
    val lonMin: Double,
    val lonMax: Double
)

class FueloClient(private val client: HttpClient) {

    suspend fun fetchStationList(subdomain: String, bounds: FueloBounds): List<FueloStation> {
        val url = "https://$subdomain.fuelo.net/ajax/get_gasstations_within_bounds_mysql_clustering"
        val response = client.submitForm(
            url = url,
            formParameters = Parameters.build {
                append("lat_min", bounds.latMin.toString())
                append("lat_max", bounds.latMax.toString())
                append("lon_min", bounds.lonMin.toString())
                append("lon_max", bounds.lonMax.toString())
                append("zoom", "14")
            }
        ) {
            header("User-Agent", "Mozilla/5.0 (compatible; Julius/1.0)")
        }
        val data = response.body<FueloListResponse>()
        if (data.status != "OK") return emptyList()

        return data.gasstations.mapNotNull { s ->
            if (s.id == null || s.cluster_count != "1") return@mapNotNull null
            val lat = s.lat.toDoubleOrNull() ?: return@mapNotNull null
            val lon = s.lon.toDoubleOrNull() ?: return@mapNotNull null
            FueloStation(id = s.id, lat = lat, lon = lon, logo = s.logo)
        }
    }

    suspend fun fetchStationInfo(subdomain: String, stationId: String): String? {
        val url = "https://$subdomain.fuelo.net/ajax/get_infowindow_content/$stationId?lang=en"
        val response = client.get(url) {
            header("User-Agent", "Mozilla/5.0 (compatible; Julius/1.0)")
            header("Accept", "application/json")
        }
        val data = response.body<FueloInfoResponse>()
        return if (data.status == "OK") data.text else null
    }
}
