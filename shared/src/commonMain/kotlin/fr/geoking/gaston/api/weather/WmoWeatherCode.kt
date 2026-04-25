package fr.geoking.gaston.api.weather

/** Short labels for WMO weather codes used by Open-Meteo [current]. */
internal fun wmoCodeLabel(code: Int): String = when (code) {
    0 -> "Clear"
    1, 2, 3 -> "Mainly clear / partly cloudy / overcast"
    45, 48 -> "Fog"
    51, 53, 55 -> "Drizzle"
    56, 57 -> "Freezing drizzle"
    61, 63, 65 -> "Rain"
    66, 67 -> "Freezing rain"
    71, 73, 75 -> "Snow"
    77 -> "Snow grains"
    80, 81, 82 -> "Rain showers"
    85, 86 -> "Snow showers"
    95 -> "Thunderstorm"
    96, 99 -> "Thunderstorm with hail"
    else -> "Weather code $code"
}
