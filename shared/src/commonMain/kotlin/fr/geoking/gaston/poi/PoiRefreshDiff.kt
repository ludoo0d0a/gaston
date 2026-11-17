package fr.geoking.gaston.poi

import kotlin.math.abs

/** User-facing fields that can change after a single-station [PoiProvider.refreshPoi]. */
enum class PoiRefreshChange {
    Prices,
    Availability,
    Status,
}

/**
 * Compares meaningful station fields between [before] and [after] for refresh feedback.
 * Ignores raw payloads / source timestamps so an unchanged price still reports "no changes".
 */
fun detectPoiRefreshChanges(before: Poi, after: Poi): List<PoiRefreshChange> {
    val changes = mutableListOf<PoiRefreshChange>()
    if (fuelPricesChanged(before.fuelPrices, after.fuelPrices)) {
        changes += PoiRefreshChange.Prices
    }
    if (availabilityChanged(before.irveDetails, after.irveDetails)) {
        changes += PoiRefreshChange.Availability
    }
    if (before.isClosed != after.isClosed) {
        changes += PoiRefreshChange.Status
    }
    return changes
}

private fun fuelPricesChanged(before: List<FuelPrice>?, after: List<FuelPrice>?): Boolean {
    val a = before.orEmpty().associateBy { it.fuelName.lowercase() }
    val b = after.orEmpty().associateBy { it.fuelName.lowercase() }
    if (a.keys != b.keys) return true
    for ((key, fpA) in a) {
        val fpB = b[key] ?: return true
        if (fpA.outOfStock != fpB.outOfStock) return true
        if (abs(fpA.price - fpB.price) > 0.0005) return true
    }
    return false
}

private fun availabilityChanged(before: IrveDetails?, after: IrveDetails?): Boolean {
    return before?.availableConnectors != after?.availableConnectors ||
        before?.totalConnectors != after?.totalConnectors
}
