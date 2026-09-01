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
import kotlin.math.PI
import kotlin.math.cos

/**
 * Global geocoding using OpenStreetMap Nominatim with detailed street and address extraction.
 * Docs: https://nominatim.org/release-docs/latest/api/Search/
 */
class NominatimGeocodingClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://nominatim.openstreetmap.org/search"
) : GeocodingClient {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun geocode(
        query: String,
        limit: Int,
        biasLatitude: Double?,
        biasLongitude: Double?
    ): List<GeocodedPlace> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()

        val url = buildString {
            append("${baseUrl}?q=${q.encodeURLParameter()}&limit=$limit&format=jsonv2&addressdetails=1")
            if (biasLatitude != null && biasLongitude != null) {
                // viewbox = west, north, east, south — boosts results inside the box without bounded=1
                val dLat = 0.45
                val cosLat = cos(PI * biasLatitude / 180.0).coerceAtLeast(0.25)
                val dLon = 0.45 / cosLat
                val left = biasLongitude - dLon
                val right = biasLongitude + dLon
                val top = biasLatitude + dLat
                val bottom = biasLatitude - dLat
                append("&viewbox=$left,$top,$right,$bottom")
            }
        }
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

            val addressObj = obj["address"]?.jsonObject
            val road = addressObj?.get("road")?.jsonPrimitive?.content
                ?: addressObj?.get("pedestrian")?.jsonPrimitive?.content
                ?: addressObj?.get("street")?.jsonPrimitive?.content
            val houseNumber = addressObj?.get("house_number")?.jsonPrimitive?.content
            val suburb = addressObj?.get("suburb")?.jsonPrimitive?.content
                ?: addressObj?.get("neighbourhood")?.jsonPrimitive?.content
            val city = addressObj?.get("city")?.jsonPrimitive?.content
                ?: addressObj?.get("town")?.jsonPrimitive?.content
                ?: addressObj?.get("village")?.jsonPrimitive?.content

            val streetPart = listOfNotNull(houseNumber, road).joinToString(" ").ifBlank { null }
            val formattedLabel = listOfNotNull(streetPart, suburb, city)
                .joinToString(", ")
                .ifBlank { null }
                ?: obj["display_name"]?.jsonPrimitive?.content
                ?: q

            GeocodedPlace(label = formattedLabel, latitude = lat, longitude = lon)
        }
    }
}
