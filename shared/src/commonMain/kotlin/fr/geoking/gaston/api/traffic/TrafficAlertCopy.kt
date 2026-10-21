package fr.geoking.gaston.api.traffic

import fr.geoking.gaston.aac.DangerZoneAlertCopy

/**
 * FR TTS / HUN copy for traffic approach alerts.
 * Examples: "accident dans 500m", "voie fermée dans 300m", "bouchon dans 2km".
 */
object TrafficAlertCopy {

    fun frLabel(severity: TrafficSeverity): String? = when (severity) {
        TrafficSeverity.Accident -> "accident"
        TrafficSeverity.Closure -> "voie fermée"
        TrafficSeverity.Congestion -> "bouchon"
        TrafficSeverity.Roadworks -> "travaux"
        TrafficSeverity.Normal, TrafficSeverity.Unknown -> null
    }

    /**
     * Spoken / notification phrase for [severity] at [distanceMeters] (threshold distance).
     * Returns null when severity is not alertable.
     */
    fun frAlert(severity: TrafficSeverity, distanceMeters: Double): String? {
        val label = frLabel(severity) ?: return null
        return "$label ${DangerZoneAlertCopy.formatDistanceForTts(distanceMeters)}"
    }

    fun isAlertable(severity: TrafficSeverity): Boolean = frLabel(severity) != null
}
