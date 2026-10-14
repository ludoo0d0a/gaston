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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * One Open-Meteo sample at a grid coordinate ([precipitation] mm from `current`).
 */
data class WeatherGridSample(
    val latitude: Double,
    val longitude: Double,
    val precipitation: Double,
)

/**
 * Fetches current precipitation for the map viewport via Open-Meteo
 * `&bounding_box=south,west,north,east` (latMin,lonMin,latMax,lonMax; no API key).
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
    suspend fun fetchCurrentPrecipitation(
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
            append("&current=precipitation")
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
            val precip = currentObj["precipitation"]?.jsonPrimitive?.doubleOrNull ?: return null

            return WeatherGridSample(
                latitude = lat,
                longitude = lon,
                precipitation = precip,
            )
        }

        private fun parseParallelArrays(root: JsonObject): List<WeatherGridSample> {
            val lats = root["latitude"]?.jsonArray ?: return emptyList()
            val lons = root["longitude"]?.jsonArray ?: return emptyList()
            val current = root["current"]

            val precips: List<Double?> = when (current) {
                is JsonArray -> current.map { el ->
                    (el as? JsonObject)?.get("precipitation")?.jsonPrimitive?.doubleOrNull
                }
                is JsonObject -> {
                    val el = current["precipitation"]
                    when (el) {
                        is JsonArray -> el.map { it.jsonPrimitive.doubleOrNull }
                        else -> listOf(el?.jsonPrimitive?.doubleOrNull)
                    }
                }
                else -> emptyList()
            }

            val n = minOf(lats.size, lons.size, precips.size)
            return (0 until n).mapNotNull { i ->
                val precip = precips[i] ?: return@mapNotNull null
                val lat = lats[i].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
                val lon = lons[i].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
                WeatherGridSample(
                    latitude = lat,
                    longitude = lon,
                    precipitation = precip,
                )
            }
        }
    }
}
