package fr.geoking.gaston.api.belib

/**
 * Combines two [BorneAvailabilityProvider]s: [primary] wins on PDC id collisions;
 * [secondary] only contributes PDCs not already present.
 */
class MergedBorneAvailabilityProvider(
    private val primary: BorneAvailabilityProvider,
    private val secondary: BorneAvailabilityProvider,
) : BorneAvailabilityProvider {

    override suspend fun getAvailability(
        latitude: Double,
        longitude: Double,
        radiusKm: Int
    ): List<PdcAvailability> {
        val primaryList = primary.getAvailability(latitude, longitude, radiusKm)
        val secondaryList = secondary.getAvailability(latitude, longitude, radiusKm)
        if (secondaryList.isEmpty()) return primaryList
        if (primaryList.isEmpty()) return secondaryList
        val primaryIds = primaryList.mapTo(HashSet()) { it.id }
        return primaryList + secondaryList.filter { it.id !in primaryIds }
    }
}
