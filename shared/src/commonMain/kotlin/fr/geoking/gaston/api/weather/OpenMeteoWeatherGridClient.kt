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
    val precipitationProbability: Double? = null,
    val precipitation: Double? = null,
    val rain: Double? = null,
    val showers: Double? = null,
    val snow: Double? = null,
)

/**
 * Fetches current [weather_code] and precipitation for coordinates in one Open-Meteo request (no API key).
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
            append("&forecast_days=1")
            append("&current=weather_code,precipitation,precipitation_probability,rain,showers,snowfall")
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
        const val MAX_POINTS = 20

        private val json = Json { ignoreUnknownKeys = true }

        /** Parses Open-Meteo multi-location JSON (array of forecast objects). */
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
                val currentObj = obj["current"]?.jsonObject ?: return@mapIndexedNotNull null
                val code = currentObj["weather_code"]?.jsonPrimitive?.intOrNull ?: return@mapIndexedNotNull null

                val precipProb = currentObj["precipitation_probability"]?.jsonPrimitive?.doubleOrNull
                val precip = currentObj["precipitation"]?.jsonPrimitive?.doubleOrNull
                val rain = currentObj["rain"]?.jsonPrimitive?.doubleOrNull
                val showers = currentObj["showers"]?.jsonPrimitive?.doubleOrNull
                val snow = (currentObj["snowfall"] ?: currentObj["snow"])?.jsonPrimitive?.doubleOrNull

                WeatherGridSample(
                    latitude = lat,
                    longitude = lon,
                    weatherCode = code,
                    precipitationProbability = precipProb,
                    precipitation = precip,
                    rain = rain,
                    showers = showers,
                    snow = snow,
                )
            }
        }

        private fun parseParallelArrays(
            root: JsonObject,
            requested: List<Pair<Double, Double>>,
        ): List<WeatherGridSample> {
            val lats = root["latitude"]?.jsonArray ?: return emptyList()
            val lons = root["longitude"]?.jsonArray ?: return emptyList()
            val current = root["current"]

            val extractField: (String) -> List<Double?> = { fieldName ->
                when (current) {
                    is JsonArray -> current.map { el ->
                        (el as? JsonObject)?.get(fieldName)?.jsonPrimitive?.doubleOrNull
                    }
                    is JsonObject -> {
                        val el = current[fieldName]
                        when (el) {
                            is JsonArray -> el.map { it.jsonPrimitive.doubleOrNull }
                            else -> listOf(el?.jsonPrimitive?.doubleOrNull)
                        }
                    }
                    else -> emptyList()
                }
            }

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

            val precipProbs = extractField("precipitation_probability")
            val precips = extractField("precipitation")
            val rains = extractField("rain")
            val showersList = extractField("showers")
            val snowList = extractField("snowfall").ifEmpty { extractField("snow") }

            val n = minOf(lats.size, lons.size, codes.size, requested.size)
            return (0 until n).mapNotNull { i ->
                val code = codes[i] ?: return@mapNotNull null
                val lat = lats[i].jsonPrimitive.doubleOrNull ?: requested[i].first
                val lon = lons[i].jsonPrimitive.doubleOrNull ?: requested[i].second
                WeatherGridSample(
                    latitude = lat,
                    longitude = lon,
                    weatherCode = code,
                    precipitationProbability = precipProbs.getOrNull(i),
                    precipitation = precips.getOrNull(i),
                    rain = rains.getOrNull(i),
                    showers = showersList.getOrNull(i),
                    snow = snowList.getOrNull(i),
                )
            }
        }
    }
}
