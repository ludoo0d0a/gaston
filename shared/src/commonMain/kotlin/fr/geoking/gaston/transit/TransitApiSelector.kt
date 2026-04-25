package fr.geoking.gaston.transit

/**
 * Selects which [TransitProvider]s to use for a given [TransitQueryContext].
 * Only providers whose region contains at least one of the context points are returned.
 */
class TransitApiSelector(
    private val providers: List<TransitProvider>
) {
    /**
     * Returns providers that cover at least one point in [context].
     * If context has no points, returns empty list (no API calls).
     */
    fun select(context: TransitQueryContext): List<TransitProvider> {
        val points = context.points()
        if (points.isEmpty()) return emptyList()
        val regions = points.mapNotNull { (lat, lon) -> TransitRegion.containing(lat, lon) }.toSet()
        if (regions.isEmpty()) return emptyList()
        return providers.filter { it.region in regions }
    }
}
