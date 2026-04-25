package fr.geoking.gaston.api.belib

import fr.geoking.gaston.poi.Poi
import kotlin.math.*

/**
 * Groups [PdcAvailability] by station (using [PdcAvailability.stationId] or by proximity),
 * builds [StationAvailabilitySummary] per group, then assigns each summary to the nearest [Poi]
 * within [maxDistanceMeters]. Returns a map from [Poi.id] to [StationAvailabilitySummary].
 */
fun matchAvailabilityToPois(
    availabilities: List<PdcAvailability>,
    pois: List<Poi>,
    maxDistanceMeters: Double = 80.0
): Map<String, StationAvailabilitySummary> {
    if (availabilities.isEmpty() || pois.isEmpty()) return emptyMap()

    // Group by station: use stationId when present, else group by rounded lat/lon (same location)
    val groupKey: (PdcAvailability) -> String = { pdc ->
        pdc.stationId?.takeIf { it.isNotBlank() }
            ?: "${roundTo5Decimals(pdc.latitude)},${roundTo5Decimals(pdc.longitude)}"
    }
    val groups = availabilities.groupBy(groupKey)

    // Per-group summary and representative point (first PDC's coords as station location)
    val stationSummaries = groups.map { (_, list) ->
        val availableCount = list.count { it.status == AvailabilityStatus.Available }
        val totalCount = list.size
        val first = list.first()
        Triple(first.latitude, first.longitude, StationAvailabilitySummary(availableCount, totalCount))
    }

    val result = mutableMapOf<String, StationAvailabilitySummary>()
    for (poi in pois) {
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
