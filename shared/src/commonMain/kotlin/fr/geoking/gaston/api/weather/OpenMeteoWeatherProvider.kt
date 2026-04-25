package fr.geoking.gaston.api.weather

import fr.geoking.gaston.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Current weather via [Open-Meteo](https://open-meteo.com/) (no API key).
 * [models] can select a regional blend, e.g. `meteofrance_seamless` for France.
 */
class OpenMeteoWeatherProvider(
    private val client: HttpClient,
    private val providerId: String,
    private val models: String? = null
) : WeatherProvider {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getWeather(latitude: Double, longitude: Double): WeatherInfo? {
        val url = buildString {
            append(BASE)
            append("?latitude=").append(latitude)
            append("&longitude=").append(longitude)
            append("&current=temperature_2m,weather_code,relative_humidity_2m,wind_speed_10m")
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
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val current = root["current"]?.jsonObject ?: return null
        val temp = current["temperature_2m"]?.jsonPrimitive?.doubleOrNull
        val wind = current["wind_speed_10m"]?.jsonPrimitive?.doubleOrNull
        val rh = current["relative_humidity_2m"]?.jsonPrimitive?.intOrNull
        val wmo = current["weather_code"]?.jsonPrimitive?.intOrNull
        val codeStr = wmo?.toString()
        val label = wmo?.let { wmoCodeLabel(it) }
        if (temp == null && wind == null && wmo == null) return null
        return WeatherInfo(
            temperatureC = temp,
            windSpeedMs = wind,
            relativeHumidityPercent = rh,
            conditionCode = codeStr,
            conditionLabel = label,
            providerId = providerId
        )
    }

    companion object {
        private const val BASE = "https://api.open-meteo.com/v1/forecast"
    }
}
