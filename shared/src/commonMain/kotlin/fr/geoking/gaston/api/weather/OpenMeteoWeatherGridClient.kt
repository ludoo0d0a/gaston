package fr.geoking.gaston.api.weather

import fr.geoking.gaston.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
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
 * Fetches current [weather_code] and precipitation for the map viewport via Open-Meteo
 * `&bounding_box=latMin,lonMin,latMax,lonMax` (no API key).
 *
 * Bounding-box queries require a concrete model (not `*_seamless` / `best_match`) and
 * do not support `timezone=auto`.
 */
class OpenMeteoWeatherGridClient(
    private val client: HttpClient,
) {
    /**
     * @param latMin south edge (must be &lt; [latMax])
     * @param lonMin west edge (must be &lt; [lonMax])
     * @param models concrete Open-Meteo model, e.g. `ecmwf_ifs025` or `meteofrance_arpege_world`
     */
    suspend fun fetchCurrentWeatherCodes(
        latMin: Double,
        lonMin: Double,
        latMax: Double,
        lonMax: Double,
        models: String = DEFAULT_MODEL,
    ): List<WeatherGridSample> {
        require(latMin < latMax && lonMin < lonMax) {
            "bounding_box requires latMin<latMax and lonMin<lonMax"
        }
        val url = buildString {
            append(BASE)
            append("?bounding_box=")
            append(latMin).append(',').append(lonMin).append(',').append(latMax).append(',').append(lonMax)
            append("&forecast_days=1")
            append("&current=weather_code,precipitation,precipitation_probability,rain,showers,snowfall")
            append("&timezone=GMT")
            append("&models=").append(models)
        }
        val response = client.get(url)
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "Open-Meteo error: ${body.take(500)}")
        }
        return parseBoundingBoxWeatherResponse(body)
    }

    companion object {
        private const val BASE = "https://api.open-meteo.com/v1/forecast"
        const val DEFAULT_MODEL = "ecmwf_ifs025"
        /** Soft UI cap for weather POIs (API may return more grid cells). */
        const val MAX_POINTS = 20

        private val json = Json { ignoreUnknownKeys = true }

        /** Parses Open-Meteo bounding-box JSON (array of cells, or a single object). */
        fun parseBoundingBoxWeatherResponse(body: String): List<WeatherGridSample> {
            val element = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
            val objects: List<JsonObject> = when (element) {
                is JsonArray -> element.mapNotNull { it as? JsonObject }
                is JsonObject -> {
                    if (element["error"]?.jsonPrimitive?.booleanOrNull == true) {
                        return emptyList()
                    }
                    if (element["latitude"] is JsonArray) {
                        return parseParallelArrays(element)
                    }
                    listOf(element)
                }
                else -> emptyList()
            }
            return objects.mapNotNull { obj -> parseCell(obj) }
        }

        /** @deprecated Prefer [parseBoundingBoxWeatherResponse]; kept for older multi-point fixtures. */
        fun parseMultiPointWeatherResponse(
            body: String,
            requested: List<Pair<Double, Double>>,
        ): List<WeatherGridSample> {
            val parsed = parseBoundingBoxWeatherResponse(body)
            if (parsed.isNotEmpty()) return parsed
            // Fallback: zip by index when response omitted lat/lon (legacy multi-point)
            val element = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
            val objects: List<JsonObject> = when (element) {
                is JsonArray -> element.mapNotNull { it as? JsonObject }
                is JsonObject -> listOf(element)
                else -> emptyList()
            }
            return objects.mapIndexedNotNull { index, obj ->
                val fallback = requested.getOrNull(index) ?: return@mapIndexedNotNull null
                parseCell(obj, fallbackLat = fallback.first, fallbackLon = fallback.second)
            }
        }

        private fun parseCell(
            obj: JsonObject,
            fallbackLat: Double? = null,
            fallbackLon: Double? = null,
        ): WeatherGridSample? {
            val lat = obj["latitude"]?.jsonPrimitive?.doubleOrNull ?: fallbackLat ?: return null
            val lon = obj["longitude"]?.jsonPrimitive?.doubleOrNull ?: fallbackLon ?: return null
            val currentObj = obj["current"]?.jsonObject ?: return null
            val code = currentObj["weather_code"]?.jsonPrimitive?.intOrNull ?: return null

            val precipProb = currentObj["precipitation_probability"]?.jsonPrimitive?.doubleOrNull
            val precip = currentObj["precipitation"]?.jsonPrimitive?.doubleOrNull
            val rain = currentObj["rain"]?.jsonPrimitive?.doubleOrNull
            val showers = currentObj["showers"]?.jsonPrimitive?.doubleOrNull
            val snow = (currentObj["snowfall"] ?: currentObj["snow"])?.jsonPrimitive?.doubleOrNull

            return WeatherGridSample(
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

        private fun parseParallelArrays(root: JsonObject): List<WeatherGridSample> {
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

            val n = minOf(lats.size, lons.size, codes.size)
            return (0 until n).mapNotNull { i ->
                val code = codes[i] ?: return@mapNotNull null
                val lat = lats[i].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
                val lon = lons[i].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
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
