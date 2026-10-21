package fr.geoking.gaston.aac

import kotlin.math.roundToInt

/**
 * FR user-facing alert copy for AAC (Level A).
 */
object DangerZoneAlertCopy {
    fun frZoneEntry(speedLimitKmH: Int?, distanceMeters: Double? = null): String {
        val speedPart = if (speedLimitKmH != null && speedLimitKmH > 0) {
            "radar ${speedLimitKmH}km/h"
        } else {
            "radar"
        }
        val distancePart = if (distanceMeters != null && distanceMeters > 0) {
            " " + formatDistanceForTts(distanceMeters)
        } else {
            ""
        }
        return "Gaston, $speedPart$distancePart"
    }

    fun formatDistanceForTts(distanceMeters: Double): String {
        return if (distanceMeters >= 1000.0) {
            val km = distanceMeters / 1000.0
            val roundedKm1Dec = ((km * 10).roundToInt()) / 10.0
            if (roundedKm1Dec % 1.0 == 0.0) {
                val kmInt = roundedKm1Dec.toInt()
                "dans ${kmInt}km"
            } else {
                val formatted = roundedKm1Dec.toString().replace('.', ',')
                "dans ${formatted}km"
            }
        } else {
            val roundedMeters = (((distanceMeters / 50.0).roundToInt()) * 50).coerceAtLeast(50)
            "dans ${roundedMeters}m"
        }
    }

    fun frZoneEntryShort(speedLimitKmH: Int?): String {
        return if (speedLimitKmH != null && speedLimitKmH > 0) {
            "Zone de danger — $speedLimitKmH km/h"
        } else {
            "Zone de danger"
        }
    }
}
