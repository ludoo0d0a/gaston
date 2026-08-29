package fr.geoking.gaston.api.belib

import fr.geoking.gaston.poi.Poi
import kotlin.math.*

/**
 * Groups [PdcAvailability] by station (using [PdcAvailability.stationId] or by proximity),
 * builds [StationAvailabilitySummary] per group, then assigns each summary to the nearest [Poi]
 * within [maxDistanceMeters]. Prefers id matches ([PdcAvailability.stationId] == [Poi.id],
 * or [PdcAvailability.id] in [fr.geoking.gaston.poi.IrveDetails.pdcIds]) before distance.
 * Returns a map from [Poi.id] to [StationAvailabilitySummary].
 */
fun matchAvailabilityToPois(
    availabilities: List<PdcAvailability>,
    pois: List<Poi>,
    maxDistanceMeters: Double = 150.0
): Map<String, StationAvailabilitySummary> {
    if (availabilities.isEmpty() || pois.isEmpty()) return emptyMap()

    val result = mutableMapOf<String, StationAvailabilitySummary>()
    val remaining = availabilities.toMutableList()

    // Prefer exact id matches (station itinerance or PDC itinerance).
    for (poi in pois) {
        val pdcIds = poi.irveDetails?.pdcIds.orEmpty()
        val matched = remaining.filter { pdc ->
            (pdc.stationId != null && pdc.stationId == poi.id) ||
                (pdcIds.isNotEmpty() && pdc.id in pdcIds)
        }
        if (matched.isEmpty()) continue
        result[poi.id] = summaryOf(matched)
        remaining.removeAll(matched.toSet())
    }
    if (remaining.isEmpty()) return result

    // Group leftover by station: use stationId when present, else group by rounded lat/lon
    val groupKey: (PdcAvailability) -> String = { pdc ->
        pdc.stationId?.takeIf { it.isNotBlank() }
            ?: "${roundTo5Decimals(pdc.latitude)},${roundTo5Decimals(pdc.longitude)}"
    }
    val groups = remaining.groupBy(groupKey)

    val stationSummaries = groups.map { (_, list) ->
        val first = list.first()
        Triple(first.latitude, first.longitude, summaryOf(list))
    }

    for (poi in pois) {
        if (poi.id in result) continue
        var bestSummary: StationAvailabilitySummary? = null
        var bestDist = maxDistanceMeters
        for ((lat, lon, summary) in stationSummaries) {
            val d = haversineMeters(poi.latitude, poi.longitude, lat, lon)
            if (d < bestDist) {
                bestDist = d
                bestSummary = summary
            }
        }
        bestSummary?.let { result[poi.id] = it }
    }
    return result
}

private fun summaryOf(list: List<PdcAvailability>): StationAvailabilitySummary {
    val availableCount = list.count { it.status == AvailabilityStatus.Available }
    return StationAvailabilitySummary(availableCount = availableCount, totalCount = list.size)
}

private fun roundTo5Decimals(x: Double): Double = round(x * 1e5) / 1e5

private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0 // meters
    val rad = PI / 180.0
    val dLat = (lat2 - lat1) * rad
    val dLon = (lon2 - lon1) * rad
    val a = sin(dLat / 2).pow(2) + cos(lat1 * rad) * cos(lat2 * rad) * sin(dLon / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1.0 - a))
    return r * c
}
