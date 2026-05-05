package fr.geoking.gaston.api.geocoding

import fr.geoking.gaston.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Global geocoding using OpenStreetMap Nominatim.
 * Docs: https://nominatim.org/release-docs/latest/api/Search/
 */
class NominatimGeocodingClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://nominatim.openstreetmap.org/search"
) : GeocodingClient {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun geocode(query: String, limit: Int): List<GeocodedPlace> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()

        val url = "${baseUrl}?q=${q.encodeURLParameter()}&limit=$limit&format=jsonv2&addressdetails=1"
        val response = client.get(url) {
            // Nominatim requires a User-Agent
            header("User-Agent", "gaston-App (contact@geoking.fr)")
        }
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "Geocoding error: $body")
        }

        val results = json.parseToJsonElement(body).jsonArray
        return results.mapNotNull { element ->
            val obj = element.jsonObject
            val lat = obj["lat"]?.jsonPrimitive?.content?.toDoubleOrNull()
            val lon = obj["lon"]?.jsonPrimitive?.content?.toDoubleOrNull()
            if (lat == null || lon == null) return@mapNotNull null
            val label = obj["display_name"]?.jsonPrimitive?.content ?: q
            val address = obj["address"]?.jsonObject
            val city = address?.get("city")?.jsonPrimitive?.content
                ?: address?.get("town")?.jsonPrimitive?.content
                ?: address?.get("village")?.jsonPrimitive?.content
                ?: address?.get("municipality")?.jsonPrimitive?.content
            GeocodedPlace(label = label, latitude = lat, longitude = lon, city = city)
        }
    }
}
