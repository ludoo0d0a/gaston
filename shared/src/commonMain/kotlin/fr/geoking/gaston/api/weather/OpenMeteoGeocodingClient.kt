package fr.geoking.gaston.api.weather

import fr.geoking.gaston.api.geocoding.GeocodedPlace
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Global place search via [Open-Meteo Geocoding](https://open-meteo.com/en/docs/geocoding-api) (no API key).
 */
class OpenMeteoGeocodingClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://geocoding-api.open-meteo.com/v1/search"
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun searchFirst(name: String): GeocodedPlace? {
        val q = name.trim()
        if (q.isEmpty()) return null
        val url = "$baseUrl?name=${q.encodeURLParameter()}&count=1"
        val response = client.get(url)
        val body = response.bodyAsText()
        if (response.status.value != 200) return null
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val results = root["results"]?.jsonArray ?: return null
        val first = results.firstOrNull()?.jsonObject ?: return null
        val lat = first["latitude"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
        val lon = first["longitude"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
        val placeName = first["name"]?.jsonPrimitive?.content ?: q
        val admin = first["admin1"]?.jsonPrimitive?.content
        val country = first["country"]?.jsonPrimitive?.content
        val label = listOfNotNull(placeName, admin, country).distinct().joinToString(", ")
        return GeocodedPlace(label = label, latitude = lat, longitude = lon)
    }
}
