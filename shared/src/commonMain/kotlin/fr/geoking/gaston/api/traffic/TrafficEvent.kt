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
    val updatedAt: Long? = null
)

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
