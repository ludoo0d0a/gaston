package fr.geoking.gaston.api.traffic

import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory

/**
 * Map pin for a [TrafficEvent] (phone list overlays use markers; AA canvas uses [Poi] pins).
 */
fun TrafficEvent.toMapPoi(providerId: String = "traffic"): Poi? {
    val lat = poiLatitude ?: return null
    val lon = poiLongitude ?: return null
    val stableId = sourceId?.takeIf { it.isNotBlank() }
        ?: "${roadRef}_${lat}_${lon}"
    return Poi(
        id = "traffic_$stableId",
        name = buildString {
            append(roadRef)
            direction?.takeIf { it.isNotBlank() }?.let { append(" ($it)") }
        },
        address = message.orEmpty(),
        latitude = lat,
        longitude = lon,
        poiCategory = PoiCategory.Traffic,
        source = providerId,
        rawSourceData = mapOf(
            RAW_SEVERITY to severity.name,
            RAW_TRAFFIC_FLAG to "1",
        ),
    )
}

fun TrafficInfo.toMapPois(): List<Poi> =
    events.mapNotNull { it.toMapPoi(providerId) }

/** Prefer non-fluid CITA segments when capping density for map pins. */
fun List<TrafficEvent>.prioritizeForMapDisplay(max: Int = FranceTrafficProvider.MAX_TRAFFIC_EVENTS): List<TrafficEvent> {
    if (size <= max) return this
    val rank = { e: TrafficEvent ->
        when (e.severity) {
            TrafficSeverity.Closure, TrafficSeverity.Accident -> 0
            TrafficSeverity.Congestion, TrafficSeverity.Roadworks -> 1
            TrafficSeverity.Unknown -> 2
            TrafficSeverity.Normal -> 3
        }
    }
    return sortedWith(compareBy(rank)).take(max)
}

const val RAW_SEVERITY = "traffic_severity"
const val RAW_TRAFFIC_FLAG = "traffic"
