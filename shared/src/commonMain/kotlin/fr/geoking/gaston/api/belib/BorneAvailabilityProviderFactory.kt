package fr.geoking.gaston.api.belib

/**
 * Returns the appropriate [BorneAvailabilityProvider] for a given location.
 * Belib (Paris) takes priority inside the Paris bbox; QualiCharge IRVE dynamique
 * covers mainland France when [isDynamicIrveEnabled] is true.
 */
class BorneAvailabilityProviderFactory(
    private val belibProvider: BorneAvailabilityProvider,
    private val qualiChargeProvider: BorneAvailabilityProvider? = null,
    private val isDynamicIrveEnabled: () -> Boolean = { false }
) {
    /** Paris bounding box (approximate). */
    private val parisLatMin = 48.81
    private val parisLatMax = 48.91
    private val parisLonMin = 2.22
    private val parisLonMax = 2.47

    /** Mainland France bounding box (approximate; excludes overseas). */
    private val franceLatMin = 41.3
    private val franceLatMax = 51.2
    private val franceLonMin = -5.2
    private val franceLonMax = 9.7

    /**
     * Returns a provider that can supply availability for the given coordinates, or null if none.
     */
    fun getProvider(latitude: Double, longitude: Double): BorneAvailabilityProvider? {
        if (latitude in parisLatMin..parisLatMax && longitude in parisLonMin..parisLonMax) {
            return belibProvider
        }
        if (
            isDynamicIrveEnabled() &&
            qualiChargeProvider != null &&
            latitude in franceLatMin..franceLatMax &&
            longitude in franceLonMin..franceLonMax
        ) {
            return qualiChargeProvider
        }
        return null
    }
}
