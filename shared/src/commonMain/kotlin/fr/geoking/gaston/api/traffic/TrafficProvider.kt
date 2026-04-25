package fr.geoking.gaston.api.traffic

/**
 * Provider of local traffic data for a given region (e.g. Luxembourg CITA).
 * Implementations fetch from country-specific APIs and map to [TrafficEvent].
 */
interface TrafficProvider {
    /** Whether this provider is currently active. */
    val enabled: Boolean get() = true

    /**
     * Fetch traffic information for the given request.
     * Returns null if the provider cannot serve this request or on error.
     */
    suspend fun getTraffic(request: TrafficRequest): TrafficInfo?
}
