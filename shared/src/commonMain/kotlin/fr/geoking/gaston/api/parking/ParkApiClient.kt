package fr.geoking.gaston.api.parking

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Client for [ParkAPI / parkendd.de](https://github.com/offenesdresden/ParkAPI).
 * City-based: GET /{city} returns lots with free, total, name, address, coords, state.
 * No auth. Cities: Zuerich, Dresden, Hamburg, Basel, Freiburg, etc.
 */
class ParkApiClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://api.parkendd.de"
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * All parking lots for a city (e.g. "Zuerich", "Dresden").
     */
    suspend fun getLots(citySlug: String): List<ParkApiLot> =
        runCatching {
            val response = client.get("$baseUrl/$citySlug")
            val body = response.bodyAsText()
            if (response.status.value != 200) return emptyList()
            val root = json.parseToJsonElement(body).jsonObject
            val lots = root["lots"]?.jsonArray ?: return emptyList()
            lots.mapNotNull { el ->
                val obj = el.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val address = obj["address"]?.jsonPrimitive?.content
                val free = obj["free"]?.jsonPrimitive?.content?.toIntOrNull()
                val total = obj["total"]?.jsonPrimitive?.content?.toIntOrNull()
                val state = obj["state"]?.jsonPrimitive?.content
                val coords = obj["coords"]?.jsonObject
                val lat = coords?.get("lat")?.jsonPrimitive?.content?.toDoubleOrNull()
                val lng = coords?.get("lng")?.jsonPrimitive?.content?.toDoubleOrNull()
                ParkApiLot(
                    id = id,
                    name = name,
                    address = address,
                    free = free,
                    total = total,
                    state = state,
                    lat = lat,
                    lon = lng
                )
            }.filter { it.lat != null && it.lon != null }
        }.getOrElse { emptyList() }
}

data class ParkApiLot(
    val id: String,
    val name: String,
    val address: String?,
    val free: Int?,
    val total: Int?,
    val state: String?,
    val lat: Double?,
    val lon: Double?
)
