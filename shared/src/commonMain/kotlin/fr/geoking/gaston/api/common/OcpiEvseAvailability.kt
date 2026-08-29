package fr.geoking.gaston.api.common

import fr.geoking.gaston.api.belib.AvailabilityStatus

/**
 * Shared OCPI EVSE status helpers used by Eco-Movement, Fastned, DKV, char.gy, Belgium NAP, etc.
 */
object OcpiEvseAvailability {

    fun isRemoved(statusRaw: String?): Boolean =
        statusRaw?.trim()?.equals("REMOVED", ignoreCase = true) == true

    fun isAvailable(statusRaw: String?): Boolean {
        val s = statusRaw?.trim()?.uppercase().orEmpty()
        return s == "AVAILABLE" || s == "FREE" || s == "IDLE"
    }

    fun mapStatus(statusRaw: String?): AvailabilityStatus {
        val s = statusRaw?.trim()?.uppercase().orEmpty()
        return when (s) {
            "AVAILABLE", "FREE", "IDLE" -> AvailabilityStatus.Available
            "CHARGING", "BLOCKED" -> AvailabilityStatus.Occupied
            "RESERVED" -> AvailabilityStatus.Reserved
            "INOPERATIVE", "OUTOFORDER" -> AvailabilityStatus.Maintenance
            "PLANNED" -> AvailabilityStatus.PlannedIntoService
            "REMOVED" -> AvailabilityStatus.Removed
            "UNKNOWN", "" -> AvailabilityStatus.Unknown
            else -> AvailabilityStatus.Unknown
        }
    }

    /**
     * Counts EVSEs from status strings. Skips [REMOVED].
     * @return availableCount to totalCount
     */
    fun counts(statuses: Iterable<String?>): Pair<Int, Int> {
        var available = 0
        var total = 0
        for (status in statuses) {
            if (isRemoved(status)) continue
            total++
            if (isAvailable(status)) available++
        }
        return available to total
    }
}
