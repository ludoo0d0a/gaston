package fr.geoking.gaston.api.weather

import fr.geoking.gaston.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Current conditions from [MET Norway Locationforecast 2.0](https://api.met.no/weatherapi/locationforecast/2.0/documentation)
 * (free, no key; requires a descriptive User-Agent per their terms).
 */
class MetNorwayWeatherProvider(
    private val client: HttpClient,
    private val userAgent: String = DEFAULT_USER_AGENT
) : WeatherProvider {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getWeather(latitude: Double, longitude: Double): WeatherInfo? {
        val url = "$BASE?lat=$latitude&lon=$longitude"
        val response = client.get(url) {
            header("User-Agent", userAgent)
        }
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "MET Norway error: ${body.take(500)}")
        }
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val props = root["properties"]?.jsonObject ?: return null
        val series = props["timeseries"]?.jsonArray ?: return null
        val first = series.firstOrNull()?.jsonObject ?: return null
        val data = first["data"]?.jsonObject ?: return null
        val instant = data["instant"]?.jsonObject ?: return null
        val details = instant["details"]?.jsonObject ?: return null
        val temp = details["air_temperature"]?.jsonPrimitive?.content?.toDoubleOrNull()
        val wind = details["wind_speed"]?.jsonPrimitive?.content?.toDoubleOrNull()
        val rh = details["relative_humidity"]?.jsonPrimitive?.content?.toDoubleOrNull()?.toInt()
        val symbol = data["next_1_hours"]?.jsonObject
            ?.get("summary")?.jsonObject
            ?.get("symbol_code")?.jsonPrimitive?.content
            ?: data["next_6_hours"]?.jsonObject
                ?.get("summary")?.jsonObject
                ?.get("symbol_code")?.jsonPrimitive?.content
        val label = symbol?.replace('_', ' ')
        if (temp == null && wind == null && symbol == null) return null
        return WeatherInfo(
            temperatureC = temp,
            windSpeedMs = wind,
            relativeHumidityPercent = rh,
            conditionCode = symbol,
            conditionLabel = label,
            providerId = PROVIDER_ID
        )
    }

    companion object {
        const val PROVIDER_ID = "met_norway"
        private const val BASE = "https://api.met.no/weatherapi/locationforecast/2.0/complete"
        private const val DEFAULT_USER_AGENT = "gaston/1.0 (fr.geoking.gaston; weather)"
    }
}
