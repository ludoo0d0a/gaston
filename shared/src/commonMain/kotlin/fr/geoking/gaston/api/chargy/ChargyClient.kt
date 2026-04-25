package fr.geoking.gaston.api.chargy

import fr.geoking.gaston.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders

/**
 * Client for Chargy Luxembourg KML real-time feed.
 * No API key required (uses a public one for now).
 */
class ChargyClient(
    private val client: HttpClient,
    private val apiKey: String = "486ac6e4-93b8-4369-9c6a-28f7c4e1a81f",
    private val baseUrl: String = "https://my.chargy.lu/b2bev-external-services/resources/kml"
) {
    suspend fun getStations(): List<ChargyStation> {
        val url = "$baseUrl?API-KEY=$apiKey"
        val response = client.get(url) {
            // Explicitly set Accept header to avoid 406 Not Acceptable
            header(HttpHeaders.Accept, "application/vnd.google-earth.kml+xml, application/xml, text/xml, */*")
        }
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "Chargy API error: $body")
        }
        return ChargyKmlParser.parse(body)
    }
}
