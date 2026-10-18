package fr.geoking.gaston.api.traffic

/**
 * Severity of a traffic event, normalized across providers.
 */
enum class TrafficSeverity {
    Normal,
    Congestion,
    Accident,
    Roadworks,
    Closure,
    Unknown
}

/**
 * Normalized traffic event or segment that any provider produces.
 * Used for map overlay, route screen, and voice.
 */
data class TrafficEvent(
    /** Road reference (e.g. "A6", "A13"). */
    val roadRef: String,
    /** Direction or stretch description, if available. */
    val direction: String? = null,
    val severity: TrafficSeverity = TrafficSeverity.Unknown,
    /** Free-text message from the source. */
    val message: String? = null,
    /** Travel time in seconds for the segment, if available. */
    val travelTimeSeconds: Int? = null,
    /** Bounding box (latMin, lonMin, latMax, lonMax) for map overlay, optional. */
    val bbox: Bbox? = null,
    /** Provider-specific event id. */
    val sourceId: String? = null,
    /** Last update timestamp from source, if available. */
    val updatedAt: Long? = null,
    /** Latitude of the primary POI location (first point of the zone/event). */
    val latitude: Double? = null,
    /** Longitude of the primary POI location (first point of the zone/event). */
    val longitude: Double? = null,
    /** Coordinates defining the zone if 2 points are different; null if points are identical or single point. */
    val zonePoints: List<Pair<Double, Double>>? = null
) {
    /** Resolved POI latitude, using [latitude] or falling back to bbox center. */
    val poiLatitude: Double?
        get() = latitude ?: bbox?.let { (it.latMin + it.latMax) / 2.0 }

    /** Resolved POI longitude, using [longitude] or falling back to bbox center. */
    val poiLongitude: Double?
        get() = longitude ?: bbox?.let { (it.lonMin + it.lonMax) / 2.0 }
}

/**
 * Bounding box: (latMin, lonMin, latMax, lonMax).
 */
data class Bbox(
    val latMin: Double,
    val lonMin: Double,
    val latMax: Double,
    val lonMax: Double
) {
    fun contains(lat: Double, lon: Double): Boolean =
        lat in latMin..latMax && lon in lonMin..lonMax
}
