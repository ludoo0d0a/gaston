package fr.geoking.gaston.api.belib

/**
 * Returns the appropriate [BorneAvailabilityProvider] for a given location.
 * For now only Belib (Paris) is supported; more providers can be added with their geographic bounds.
 */
class BorneAvailabilityProviderFactory(
    private val belibProvider: BorneAvailabilityProvider
) {
    /** Paris bounding box (approximate). */
    private val parisLatMin = 48.81
    private val parisLatMax = 48.91
    private val parisLonMin = 2.22
    private val parisLonMax = 2.47

    /**
     * Returns a provider that can supply availability for the given coordinates, or null if none.
     */
    fun getProvider(latitude: Double, longitude: Double): BorneAvailabilityProvider? {
        if (latitude in parisLatMin..parisLatMax && longitude in parisLonMin..parisLonMax) {
            return belibProvider
        }
        return null
    }
}
