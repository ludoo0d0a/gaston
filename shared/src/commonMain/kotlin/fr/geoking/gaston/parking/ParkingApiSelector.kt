package fr.geoking.gaston.parking

/**
 * Selects which [ParkingProvider]s to use based on the user's location (country/region).
 * - Prefers providers that [servedRegions] match the context's region (e.g. Germany → LiveParking/ParkAPI).
 * - Uses [isFallbackOnly] providers (e.g. OSM) only when no country-specific provider serves that region.
 */
class ParkingApiSelector(
    private val providers: List<ParkingProvider>
) {
    /**
     * Returns providers to call for [context]:
     * - Region-specific providers that serve the region(s) of the context points and cover the point.
     * - If no such provider, adds fallback-only providers (e.g. OSM) so the user still gets results.
     */
    fun select(context: ParkingQueryContext): List<ParkingProvider> {
        val points = context.points()
        if (points.isEmpty()) return emptyList()

        val regionSpecific = providers.filter { !it.isFallbackOnly() }
        val fallbackOnly = providers.filter { it.isFallbackOnly() }

        val regionsAtPoints = points.mapNotNull { (lat, lon) -> ParkingRegion.containing(lat, lon) }.toSet()

        val selected = mutableSetOf<ParkingProvider>()

        for ((lat, lon) in points) {
            val region = ParkingRegion.containing(lat, lon)
            val regionSpecificForPoint = regionSpecific.filter { provider ->
                provider.covers(lat, lon) &&
                    (provider.servedRegions().isEmpty() || region != null && region in provider.servedRegions())
            }
            if (regionSpecificForPoint.isNotEmpty()) {
                selected.addAll(regionSpecificForPoint)
            } else {
                // No country-specific provider for this point: use fallback (e.g. OSM)
                selected.addAll(fallbackOnly.filter { it.covers(lat, lon) })
            }
        }

        return selected.toList()
    }
}
