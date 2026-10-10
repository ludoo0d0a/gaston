package fr.geoking.gaston.api.weather

/**
 * Marker family for Open-Meteo / WMO [weather_code] values.
 * Shared icons are chosen per family; intensity uses [WmoWeatherStyle.discArgb].
 */
enum class WmoWeatherFamily {
    Clear,
    Cloudy,
    Fog,
    Drizzle,
    Rain,
    Showers,
    Snow,
    Freezing,
    Storm,
    Unknown,
}

/**
 * Display style for a single WMO weather code (label + family + disc color ARGB).
 */
data class WmoWeatherStyle(
    val code: Int,
    val label: String,
    val family: WmoWeatherFamily,
    /** Fully opaque ARGB disc fill for map markers. */
    val discArgb: Int,
)

/** Short labels for WMO weather codes used by Open-Meteo [current]. */
fun wmoCodeLabel(code: Int): String = wmoWeatherStyle(code).label

/** Full style (label, family, color) for an Open-Meteo WMO weather code. */
fun wmoWeatherStyle(code: Int): WmoWeatherStyle = when (code) {
    0 -> WmoWeatherStyle(0, "Clear", WmoWeatherFamily.Clear, 0xFFFBBF24.toInt())
    1 -> WmoWeatherStyle(1, "Mainly clear", WmoWeatherFamily.Clear, 0xFFFCD34D.toInt())
    2 -> WmoWeatherStyle(2, "Partly cloudy", WmoWeatherFamily.Cloudy, 0xFF94A3B8.toInt())
    3 -> WmoWeatherStyle(3, "Overcast", WmoWeatherFamily.Cloudy, 0xFF64748B.toInt())
    45 -> WmoWeatherStyle(45, "Fog", WmoWeatherFamily.Fog, 0xFFCBD5E1.toInt())
    48 -> WmoWeatherStyle(48, "Depositing rime fog", WmoWeatherFamily.Fog, 0xFF94A3B8.toInt())
    51 -> WmoWeatherStyle(51, "Light drizzle", WmoWeatherFamily.Drizzle, 0xFF7DD3FC.toInt())
    53 -> WmoWeatherStyle(53, "Moderate drizzle", WmoWeatherFamily.Drizzle, 0xFF38BDF8.toInt())
    55 -> WmoWeatherStyle(55, "Dense drizzle", WmoWeatherFamily.Drizzle, 0xFF0EA5E9.toInt())
    56 -> WmoWeatherStyle(56, "Light freezing drizzle", WmoWeatherFamily.Freezing, 0xFFA5B4FC.toInt())
    57 -> WmoWeatherStyle(57, "Dense freezing drizzle", WmoWeatherFamily.Freezing, 0xFF818CF8.toInt())
    61 -> WmoWeatherStyle(61, "Slight rain", WmoWeatherFamily.Rain, 0xFF60A5FA.toInt())
    63 -> WmoWeatherStyle(63, "Moderate rain", WmoWeatherFamily.Rain, 0xFF3B82F6.toInt())
    65 -> WmoWeatherStyle(65, "Heavy rain", WmoWeatherFamily.Rain, 0xFF1D4ED8.toInt())
    66 -> WmoWeatherStyle(66, "Light freezing rain", WmoWeatherFamily.Freezing, 0xFF6366F1.toInt())
    67 -> WmoWeatherStyle(67, "Heavy freezing rain", WmoWeatherFamily.Freezing, 0xFF4F46E5.toInt())
    71 -> WmoWeatherStyle(71, "Slight snow", WmoWeatherFamily.Snow, 0xFFE0F2FE.toInt())
    73 -> WmoWeatherStyle(73, "Moderate snow", WmoWeatherFamily.Snow, 0xFFBAE6FD.toInt())
    75 -> WmoWeatherStyle(75, "Heavy snow", WmoWeatherFamily.Snow, 0xFF7DD3FC.toInt())
    77 -> WmoWeatherStyle(77, "Snow grains", WmoWeatherFamily.Snow, 0xFF67E8F9.toInt())
    80 -> WmoWeatherStyle(80, "Slight rain showers", WmoWeatherFamily.Showers, 0xFF93C5FD.toInt())
    81 -> WmoWeatherStyle(81, "Moderate rain showers", WmoWeatherFamily.Showers, 0xFF3B82F6.toInt())
    82 -> WmoWeatherStyle(82, "Violent rain showers", WmoWeatherFamily.Showers, 0xFF1E40AF.toInt())
    85 -> WmoWeatherStyle(85, "Slight snow showers", WmoWeatherFamily.Snow, 0xFFA5F3FC.toInt())
    86 -> WmoWeatherStyle(86, "Heavy snow showers", WmoWeatherFamily.Snow, 0xFF22D3EE.toInt())
    95 -> WmoWeatherStyle(95, "Thunderstorm", WmoWeatherFamily.Storm, 0xFFF59E0B.toInt())
    96 -> WmoWeatherStyle(96, "Thunderstorm with slight hail", WmoWeatherFamily.Storm, 0xFFF97316.toInt())
    99 -> WmoWeatherStyle(99, "Thunderstorm with heavy hail", WmoWeatherFamily.Storm, 0xFFEA580C.toInt())
    else -> WmoWeatherStyle(code, "Weather code $code", WmoWeatherFamily.Unknown, 0xFF9CA3AF.toInt())
}

/** Raw keys stored on weather [fr.geoking.gaston.poi.Poi.rawSourceData]. */
object WeatherPoiRawKeys {
    const val WEATHER_CODE = "weather_code"
    const val WMO_FAMILY = "wmo_family"
    const val SOURCE = "open_meteo"
}
