package fr.geoking.gaston.poi

import fr.geoking.gaston.api.belib.StationAvailabilitySummary

/** True for EV charging stations (explicit flag or IRVE / battery-swap category). */
val Poi.isChargingStation: Boolean
    get() = isElectric ||
        poiCategory == PoiCategory.Irve ||
        poiCategory == PoiCategory.BatterySwap

/** Availability embedded in [IrveDetails] (e.g. Chargy KML, Char.gy OCPI). */
fun Poi.embeddedAvailabilitySummary(): StationAvailabilitySummary? {
    if (!isChargingStation) return null
    val irve = irveDetails ?: return null
    val total = irve.totalConnectors ?: return null
    if (total <= 0) return null
    val available = irve.availableConnectors ?: return null
    return StationAvailabilitySummary(
        availableCount = available.coerceIn(0, total),
        totalCount = total,
    )
}

/**
 * Prefer live [external] availability from Belib / QualiCharge / Belgium NAP when present;
 * otherwise fall back to [embeddedAvailabilitySummary].
 */
fun Poi.resolveAvailabilitySummary(
    external: StationAvailabilitySummary? = null,
): StationAvailabilitySummary? {
    external?.takeIf { it.totalCount > 0 }?.let { return it }
    return embeddedAvailabilitySummary()
}

fun resolveAvailabilitySummary(
    poi: Poi,
    availabilityByPoiId: Map<String, StationAvailabilitySummary>,
): StationAvailabilitySummary? = poi.resolveAvailabilitySummary(availabilityByPoiId[poi.id])
