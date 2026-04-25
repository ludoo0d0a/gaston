package fr.geoking.gaston.api.traffic

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode

/**
 * Fetches CITA motorway service-level GeoJSON (Luxembourg).
 * No API key. Data © CITA / Administration des Ponts et Chaussées.
 *
 * @see <a href="https://cita.lu/">cita.lu</a>
 */
class CitaGeoJsonTrafficClient(
    private val client: HttpClient,
    private val geoJsonUrl: String = "https://cita.lu/geojson/niveau_service_geojson.json"
) {
    suspend fun fetchGeoJson(): String? = try {
        val response = client.get(geoJsonUrl)
        if (response.status == HttpStatusCode.OK) response.bodyAsText() else null
    } catch (_: Exception) {
        null
    }
}
