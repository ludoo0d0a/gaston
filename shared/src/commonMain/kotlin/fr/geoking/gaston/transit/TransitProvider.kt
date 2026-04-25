package fr.geoking.gaston.transit

/**
 * Per-country/region transit API. Implementations fetch from RATP, mobiliteit.lu, STIB, etc.
 * The [TransitApiSelector] chooses which providers to call based on location.
 */
interface TransitProvider {
    val id: String
    val region: TransitRegion

    /** True if this provider covers the given point (default: region bbox). */
    fun covers(lat: Double, lon: Double): Boolean = region.contains(lat, lon)

    /**
     * Stops near (lat, lon) within [radiusMeters].
     * Return empty list on error or unsupported; callers merge from multiple providers.
     */
    suspend fun getStopsNearby(lat: Double, lon: Double, radiusMeters: Int): List<TransitStop>

    /**
     * Next departures at the given stop (provider-specific stop id).
     * Return empty list on error or unknown stop.
     */
    suspend fun getDepartures(stopId: String): List<TransitDeparture>
}
