package fr.geoking.gaston.transit

/**
 * Transport mode for a stop or departure (bus, tram, train, etc.).
 */
enum class TransitMode {
    Bus,
    Tram,
    Metro,
    Train,
    Ferry,
    Other
}

/**
 * Unified transit stop (bus/tram/metro/train) from any provider.
 */
data class TransitStop(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val mode: TransitMode = TransitMode.Other,
    val operatorId: String? = null,
    /** Provider id (e.g. "fr_ratp") for attribution and deduplication. */
    val providerId: String? = null
)

/**
 * Unified departure at a stop (next passage).
 */
data class TransitDeparture(
    val stopId: String,
    val lineId: String,
    val lineName: String,
    val direction: String,
    /** Scheduled departure time as ISO-8601 or "HH:mm" string. */
    val scheduledTime: String,
    /** Realtime departure if available. */
    val realtimeTime: String? = null,
    /** Delay in minutes; null if on time or unknown. */
    val delayMinutes: Int? = null,
    val mode: TransitMode = TransitMode.Other,
    val providerId: String? = null
)
