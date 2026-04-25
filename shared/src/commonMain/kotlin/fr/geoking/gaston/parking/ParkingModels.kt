package fr.geoking.gaston.parking

/**
 * Unified parking POI from any provider (LiveParking, ParkAPI, OSM).
 * Fields are null when the source API does not provide them.
 */
data class ParkingPoi(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    /** Total capacity when known (e.g. LiveParking, ParkAPI). */
    val capacity: Int? = null,
    /** Currently available spaces when known. */
    val available: Int? = null,
    /** Opening hours as text (e.g. OSM opening_hours, or "open"/"closed"). */
    val openingHours: String? = null,
    /** Price info as free text (e.g. "2€/h", "free first 30 min"). */
    val priceInfo: String? = null,
    /** Provider id for attribution and deduplication (e.g. "liveparking", "parkapi", "osm"). */
    val providerId: String? = null,
    val address: String? = null,
    /** Status when provided: "open", "closed", "active", etc. */
    val state: String? = null,
    /** Distance in km when returned by provider (e.g. LiveParking with lat/lon query). */
    val distanceKm: Double? = null
)
