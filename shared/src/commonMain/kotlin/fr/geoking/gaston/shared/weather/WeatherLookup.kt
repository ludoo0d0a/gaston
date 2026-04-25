package fr.geoking.gaston.shared.weather

import fr.geoking.gaston.shared.action.ActionResult

/**
 * Resolves a place (or current device position) and fetches live weather for [ActionType.GET_WEATHER].
 * Platform code loads map/network deps and calls regional [fr.geoking.gaston.api.weather.WeatherProvider]s.
 */
fun interface WeatherLookup {
    /**
     * @param locationQuery place name to geocode, or null/blank for the user's current coordinates.
     */
    suspend fun getCurrentWeather(locationQuery: String?): ActionResult
}
