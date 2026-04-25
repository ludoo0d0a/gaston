package fr.geoking.gaston.api.traffic

/**
 * Returns the appropriate [TrafficProvider] for a given location.
 * Holds a list of (region, provider); adding a country is a single "add region + provider" step.
 */
class TrafficProviderFactory(
    private val regionsAndProviders: List<Pair<GeographicRegion, TrafficProvider>>
) {
    /**
     * Returns a provider that can supply traffic for the given coordinates, or null if none.
     */
    fun getProvider(latitude: Double, longitude: Double): TrafficProvider? =
        regionsAndProviders.firstOrNull { (region, provider) -> provider.enabled && region.contains(latitude, longitude) }?.second

    /**
     * Returns all providers that cover any part of the route (e.g. Luxembourg + France).
     * Callers can merge [TrafficInfo] or show per-provider.
     */
    fun getProvidersForRoute(routePoints: List<Pair<Double, Double>>): List<TrafficProvider> {
        if (routePoints.isEmpty()) return emptyList()
        val seen = mutableSetOf<TrafficProvider>()
        for ((lat, lon) in routePoints) {
            getProvider(lat, lon)?.let { provider ->
                if (provider.enabled && provider !in seen) {
                    seen.add(provider)
                }
            }
        }
        return seen.toList()
    }
}
