package fr.geoking.gaston.api.traffic

/**
 * Common repository interface for persisting and querying traffic events in Room (or in-memory cache).
 */
interface TrafficRepository {

    /**
     * Retrieves traffic events from the local database matching the given bounding box
     * and non-expired sources.
     */
    suspend fun getTrafficInBbox(
        latMin: Double,
        lonMin: Double,
        latMax: Double,
        lonMax: Double,
        maxEvents: Int = 250,
        /** When set, only events from this DATEX / provider source (e.g. `bison_fute_rrn`). */
        sourceId: String? = null,
    ): List<TrafficEvent>

    /**
     * Checks whether the cached traffic events for a specific source/provider
     * are still valid according to the specified TTL in milliseconds.
     */
    suspend fun isSourceFresh(sourceId: String, ttlMs: Long = DEFAULT_TTL_MS): Boolean

    /**
     * Replaces all events for a given source with new events and updates the source's fetch timestamp.
     */
    suspend fun replaceEventsForSource(
        sourceId: String,
        events: List<TrafficEvent>,
        fetchedAtMs: Long = System.currentTimeMillis()
    )

    /**
     * Lazily updates zone/polygon points for a specific event (e.g. when road shape is resolved from OSM/Overpass).
     */
    suspend fun updateZonePoints(eventId: String, sourceId: String, zonePoints: List<Pair<Double, Double>>)

    /**
     * Clears cached traffic data for all or specific sources.
     */
    suspend fun clear(sourceId: String? = null)

    companion object {
        const val DEFAULT_TTL_MS = 60 * 60_000L // 1 hour
    }
}
