package fr.geoking.gaston.api.finland

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText

class PolttoaineClient(private val client: HttpClient) {
    private val baseUrl = "https://www.polttoaine.net"

    suspend fun fetchCityPage(city: String): String {
        val response = client.get("$baseUrl/$city") {
            header("Accept", "text/html")
            header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        }
        return response.bodyAsText()
    }

    suspend fun fetchCoordinates(mapId: String): Pair<Double, Double>? {
        val response = client.get("$baseUrl/index.php?cmd=map&id=$mapId") {
            header("Accept", "text/html")
            header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        }
        val html = response.bodyAsText()
        val coordMatch = Regex("new\\s+google\\.maps\\.LatLng\\(\\s*([-\\d.]+)\\s*,\\s*([-\\d.]+)\\s*\\)").find(html)
        if (coordMatch != null) {
            val lat = coordMatch.groupValues[1].toDoubleOrNull()
            val lon = coordMatch.groupValues[2].toDoubleOrNull()
            if (lat != null && lon != null) return Pair(lat, lon)
        }
        return null
    }
}
