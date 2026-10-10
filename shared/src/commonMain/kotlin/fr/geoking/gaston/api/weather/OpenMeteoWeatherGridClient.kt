package fr.geoking.gaston.api.weather

import fr.geoking.gaston.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * One Open-Meteo sample at a grid coordinate (WMO [weatherCode] as returned by the API).
 */
data class WeatherGridSample(
    val latitude: Double,
    val longitude: Double,
    val weatherCode: Int,
)

/**
 * Fetches current [weather_code] for many coordinates in one Open-Meteo request (no API key).
 */
class OpenMeteoWeatherGridClient(
    private val client: HttpClient,
) {
    /**
     * @param points lat/lon pairs (max [MAX_POINTS] used)
     * @param models optional Open-Meteo model blend, e.g. `meteofrance_seamless`
     */
    suspend fun fetchCurrentWeatherCodes(
        points: List<Pair<Double, Double>>,
        models: String? = null,
    ): List<WeatherGridSample> {
        if (points.isEmpty()) return emptyList()
        val limited = points.take(MAX_POINTS)
        val lats = limited.joinToString(",") { it.first.toString() }
        val lons = limited.joinToString(",") { it.second.toString() }
        val url = buildString {
            append(BASE)
            append("?latitude=").append(lats)
            append("&longitude=").append(lons)
            append("&current=weather_code")
            append("&timezone=auto")
            if (models != null) {
                append("&models=").append(models)
            }
        }
        val response = client.get(url)
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "Open-Meteo error: ${body.take(500)}")
        }
        return parseMultiPointWeatherResponse(body, limited)
    }

    companion object {
        private const val BASE = "https://api.open-meteo.com/v1/forecast"
        const val MAX_POINTS = 36

        private val json = Json { ignoreUnknownKeys = true }

        /** Parses Open-Meteo multi-location `current=weather_code` JSON (array of forecast objects). */
        fun parseMultiPointWeatherResponse(
            body: String,
            requested: List<Pair<Double, Double>>,
        ): List<WeatherGridSample> {
            val element = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
            val objects: List<JsonObject> = when (element) {
                is JsonArray -> element.mapNotNull { it as? JsonObject }
                is JsonObject -> listOf(element)
                else -> emptyList()
            }
            if (objects.isEmpty()) return emptyList()

            if (objects.size == 1) {
                val root = objects[0]
                if (root["latitude"] is JsonArray) {
                    return parseParallelArrays(root, requested)
                }
            }

            return objects.mapIndexedNotNull { index, obj ->
                val fallback = requested.getOrNull(index) ?: return@mapIndexedNotNull null
                val lat = obj["latitude"]?.jsonPrimitive?.doubleOrNull ?: fallback.first
                val lon = obj["longitude"]?.jsonPrimitive?.doubleOrNull ?: fallback.second
                val code = obj["current"]?.jsonObject
                    ?.get("weather_code")?.jsonPrimitive?.intOrNull
                    ?: return@mapIndexedNotNull null
                WeatherGridSample(lat, lon, code)
            }
        }

        private fun parseParallelArrays(
            root: JsonObject,
            requested: List<Pair<Double, Double>>,
        ): List<WeatherGridSample> {
            val lats = root["latitude"]?.jsonArray ?: return emptyList()
            val lons = root["longitude"]?.jsonArray ?: return emptyList()
            val current = root["current"]
            val codes: List<Int?> = when (current) {
                is JsonArray -> current.map { el ->
                    (el as? JsonObject)?.get("weather_code")?.jsonPrimitive?.intOrNull
                }
                is JsonObject -> {
                    val codeEl = current["weather_code"]
                    when (codeEl) {
                        is JsonArray -> codeEl.map { it.jsonPrimitive.intOrNull }
                        else -> listOf(codeEl?.jsonPrimitive?.intOrNull)
                    }
                }
                else -> emptyList()
            }
            val n = minOf(lats.size, lons.size, codes.size, requested.size)
            return (0 until n).mapNotNull { i ->
                val code = codes[i] ?: return@mapNotNull null
                val lat = lats[i].jsonPrimitive.doubleOrNull ?: requested[i].first
                val lon = lons[i].jsonPrimitive.doubleOrNull ?: requested[i].second
                WeatherGridSample(lat, lon, code)
            }
        }
    }
}
