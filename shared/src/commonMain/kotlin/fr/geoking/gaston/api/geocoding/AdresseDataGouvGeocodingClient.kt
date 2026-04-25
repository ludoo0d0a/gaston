package fr.geoking.gaston.api.geocoding

import fr.geoking.gaston.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Lightweight geocoding using `api-adresse.data.gouv.fr` (France).
 * Docs: https://adresse.data.gouv.fr/api-doc/adresse
 */
class AdresseDataGouvGeocodingClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://api-adresse.data.gouv.fr/search/"
) : GeocodingClient {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun geocode(query: String, limit: Int): List<GeocodedPlace> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()

        val url = "${baseUrl}?q=${q.encodeURLParameter()}&limit=$limit"
        val response = client.get(url)
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "Geocoding error: $body")
        }

        val root = json.parseToJsonElement(body).jsonObject
        val features = root["features"]?.jsonArray ?: return emptyList()
        return features.mapNotNull { feature ->
            val obj = feature.jsonObject
            val props = obj["properties"]?.jsonObject
            val geometry = obj["geometry"]?.jsonObject
            val coords = geometry?.get("coordinates")?.jsonArray
            val lon = coords?.getOrNull(0)?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            val lat = coords?.getOrNull(1)?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            if (lat == null || lon == null) return@mapNotNull null
            val label = props?.get("label")?.jsonPrimitive?.contentOrNull
                ?: props?.get("name")?.jsonPrimitive?.contentOrNull
                ?: q
            GeocodedPlace(label = label, latitude = lat, longitude = lon)
        }
    }
}

