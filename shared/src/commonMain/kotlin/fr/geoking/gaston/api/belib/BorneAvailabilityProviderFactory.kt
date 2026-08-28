package fr.geoking.gaston.api.belib

/**
 * Returns the appropriate [BorneAvailabilityProvider] for a given location.
 *
 * - **Belgium:** NAP open Road Public Charging Network dump (transportdata.be)
 * - **France:** QualiCharge IRVE dynamique; in Paris, Belib is merged as secondary
 */
class BorneAvailabilityProviderFactory(
    private val belibProvider: BorneAvailabilityProvider,
    private val qualiChargeProvider: BorneAvailabilityProvider? = null,
    private val belgiumNapProvider: BorneAvailabilityProvider? = null,
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

    /** Belgium bounding box (approximate). Checked before France to avoid FR/BE overlap. */
    private val belgiumLatMin = 49.45
    private val belgiumLatMax = 51.55
    private val belgiumLonMin = 2.5
    private val belgiumLonMax = 6.45

    private val parisMergedProvider: BorneAvailabilityProvider? =
        qualiChargeProvider?.let { MergedBorneAvailabilityProvider(primary = it, secondary = belibProvider) }

    /**
     * Returns a provider that can supply availability for the given coordinates, or null if none.
     */
    fun getProvider(latitude: Double, longitude: Double): BorneAvailabilityProvider? {
        val inBelgium =
            latitude in belgiumLatMin..belgiumLatMax &&
                longitude in belgiumLonMin..belgiumLonMax
        if (inBelgium && belgiumNapProvider != null) {
            return belgiumNapProvider
        }

        val inFrance =
            latitude in franceLatMin..franceLatMax &&
                longitude in franceLonMin..franceLonMax
        if (!inFrance) return null

        val inParis =
            latitude in parisLatMin..parisLatMax &&
                longitude in parisLonMin..parisLonMax

        return when {
            inParis && parisMergedProvider != null -> parisMergedProvider
            inParis -> belibProvider
            qualiChargeProvider != null -> qualiChargeProvider
            else -> null
        }
    }
}
