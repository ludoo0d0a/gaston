package fr.geoking.gaston.api.belib

import fr.geoking.gaston.parking.ParkingRegion

/**
 * Returns the appropriate [BorneAvailabilityProvider] for a given location.
 *
 * Country assignment uses [ParkingRegion] (non-overlapping country sub-boxes where defined):
 * - **Belgium:** NAP open Road Public Charging Network dump (transportdata.be)
 * - **France:** QualiCharge IRVE dynamique; in Paris, Belib is merged as secondary
 * - **Everywhere else** (LU, DE, NL, …): Eco-Movement OCPI when configured
 */
class BorneAvailabilityProviderFactory(
    private val belibProvider: BorneAvailabilityProvider,
    private val qualiChargeProvider: BorneAvailabilityProvider? = null,
    private val belgiumNapProvider: BorneAvailabilityProvider? = null,
    private val ecoMovementProvider: BorneAvailabilityProvider? = null,
) {
    /** Paris bounding box (approximate; nested inside France). */
    private val parisLatMin = 48.81
    private val parisLatMax = 48.91
    private val parisLonMin = 2.22
    private val parisLonMax = 2.47

    private val parisMergedProvider: BorneAvailabilityProvider? =
        qualiChargeProvider?.let { MergedBorneAvailabilityProvider(primary = it, secondary = belibProvider) }

    /**
     * Returns a provider that can supply availability for the given coordinates, or null if none.
     */
    fun getProvider(latitude: Double, longitude: Double): BorneAvailabilityProvider? {
        return when (ParkingRegion.containing(latitude, longitude)) {
            ParkingRegion.Belgium -> belgiumNapProvider ?: ecoMovementProvider
            ParkingRegion.France -> {
                val inParis =
                    latitude in parisLatMin..parisLatMax &&
                        longitude in parisLonMin..parisLonMax
                when {
                    inParis && parisMergedProvider != null -> parisMergedProvider
                    inParis -> belibProvider
                    qualiChargeProvider != null -> qualiChargeProvider
                    else -> ecoMovementProvider
                }
            }
            else -> ecoMovementProvider
        }
    }
}
