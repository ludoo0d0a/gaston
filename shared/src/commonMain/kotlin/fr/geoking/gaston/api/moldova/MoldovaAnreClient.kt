package fr.geoking.gaston.api.moldova

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp

@Serializable
data class ANREStation(
    val x: Double,
    val y: Double,
    val station_status: Int,
    val station_name: String,
    val company_name: String,
    val idno: String,
    val diesel: Double? = null,
    val gasoline: Double? = null,
    val gpl: Double? = null,
    val fullstreet: String? = null,
    val addrnum: String? = null
)

class MoldovaAnreClient(private val client: HttpClient) {
    private val apiUrl = "https://api.ecarburanti.anre.md/public/"

    suspend fun fetchAllStations(): List<ANREStation> {
        val response = client.get(apiUrl) {
            header("Accept", "application/json")
            header("User-Agent", "Gaston/1.0")
        }
        return response.body<List<ANREStation>>()
    }

    /** EPSG:3857 (Web Mercator) → EPSG:4326 (WGS84) */
    fun webMercatorToLatLon(x: Double, y: Double): Pair<Double, Double> {
        val lon = (x / 20037508.342789244) * 180
        val latRad = atan(exp((y / 20037508.342789244) * PI))
        val lat = latRad * (360 / PI) - 90
        return Pair(lat, lon)
    }
}
