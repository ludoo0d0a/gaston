package fr.geoking.gaston.aac

/**
 * FR user-facing alert copy for AAC (Level A).
 * Never mentions radar location / distance-to-control.
 */
object DangerZoneAlertCopy {
    fun frZoneEntry(speedLimitKmH: Int?): String {
        return if (speedLimitKmH != null && speedLimitKmH > 0) {
            "radar $speedLimitKmH km heure"
        } else {
            "radar"
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
