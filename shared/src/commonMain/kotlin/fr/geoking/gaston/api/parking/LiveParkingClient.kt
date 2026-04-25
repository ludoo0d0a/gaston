package fr.geoking.gaston.api.parking

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Client for [LiveParking API](https://liveparking.eu/docs/) (Europe, e.g. Berlin, Cologne).
 * No auth, 60 req/min. Returns availability and capacity; no prices or opening hours.
 */
class LiveParkingClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://liveparking.eu/api/v1"
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parking locations near (lat, lon). API returns locations sorted by distance with `distance` in km.
     */
    suspend fun getLocations(lat: Double, lon: Double, limit: Int = 50): List<LiveParkingLocation> =
        runCatching {
            val response = client.get("$baseUrl/locations") {
                parameter("lat", lat)
                parameter("lon", lon)
                parameter("limit", limit)
            }
            val body = response.bodyAsText()
            if (response.status.value != 200) return emptyList()
            val root = json.parseToJsonElement(body).jsonObject
            val locations = root["locations"]?.jsonArray ?: return emptyList()
            locations.mapNotNull { el ->
                val obj = el.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val coords = obj["coordinates"]?.jsonObject
                    ?: return@mapNotNull null
                val latVal = coords["lat"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    ?: return@mapNotNull null
                val lngVal = coords["lng"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    ?: return@mapNotNull null
                val available = obj["available"]?.jsonPrimitive?.content?.toIntOrNull()
                val capacity = obj["capacity"]?.jsonPrimitive?.content?.toIntOrNull()
                val status = obj["status"]?.jsonPrimitive?.content
                val distance = obj["distance"]?.jsonPrimitive?.content?.toDoubleOrNull()
                LiveParkingLocation(
                    id = id,
                    name = name,
                    lat = latVal,
                    lon = lngVal,
                    available = available,
                    capacity = capacity,
                    status = status,
                    distanceKm = distance
                )
            }
        }.getOrElse { emptyList() }
}

data class LiveParkingLocation(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val available: Int?,
    val capacity: Int?,
    val status: String?,
    val distanceKm: Double?
)
