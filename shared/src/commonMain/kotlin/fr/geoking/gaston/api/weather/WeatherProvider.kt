package fr.geoking.gaston.api.weather

/**
 * Fetches current weather for a geographic point. Implementations may call different free APIs
 * by region; see [WeatherProviderFactory].
 */
fun interface WeatherProvider {
    suspend fun getWeather(latitude: Double, longitude: Double): WeatherInfo?
}
