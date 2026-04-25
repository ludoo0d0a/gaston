package fr.geoking.gaston.api.weather

/**
 * Current conditions at a point, from a [WeatherProvider].
 */
data class WeatherInfo(
    val temperatureC: Double?,
    val windSpeedMs: Double?,
    val relativeHumidityPercent: Int?,
    /** Raw code from the backend (e.g. WMO code for Open-Meteo, symbol_code for MET Norway). */
    val conditionCode: String?,
    val conditionLabel: String?,
    val providerId: String
)
