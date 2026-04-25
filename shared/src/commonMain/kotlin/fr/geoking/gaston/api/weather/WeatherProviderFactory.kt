package fr.geoking.gaston.api.weather

import fr.geoking.gaston.api.traffic.GeographicRegion

/**
 * Picks a [WeatherProvider] by coordinates, same pattern as [fr.geoking.gaston.api.traffic.TrafficProviderFactory].
 * Register more specific [GeographicRegion.Bbox] entries before [GeographicRegion.Everywhere].
 */
class WeatherProviderFactory(
    private val regionsAndProviders: List<Pair<GeographicRegion, WeatherProvider>>
) {
    fun getProvider(latitude: Double, longitude: Double): WeatherProvider? =
        regionsAndProviders.firstOrNull { (region, _) -> region.contains(latitude, longitude) }?.second

    fun getProvidersForRoute(routePoints: List<Pair<Double, Double>>): List<WeatherProvider> {
        if (routePoints.isEmpty()) return emptyList()
        val seen = LinkedHashSet<WeatherProvider>()
        for ((lat, lon) in routePoints) {
            getProvider(lat, lon)?.let { seen.add(it) }
        }
        return seen.toList()
    }
}
