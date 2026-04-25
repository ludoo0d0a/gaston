package fr.geoking.gaston.api.traffic

/**
 * Result of a traffic request from a [TrafficProvider].
 * All events use the shared [TrafficEvent] model.
 */
data class TrafficInfo(
    val events: List<TrafficEvent>,
    /** Identifier of the provider (e.g. "cita", "digitraffic"). */
    val providerId: String
)
