package fr.geoking.gaston.api.traffic

import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val defaultEnricherScope = CoroutineScope(Dispatchers.Default)

/**
 * Map pin for a [TrafficEvent] (phone list overlays use markers; AA canvas uses [Poi] pins).
 */
fun TrafficEvent.toMapPoi(
    providerId: String = "traffic",
    enricher: TrafficPolygonEnricher? = null,
): Poi? {
    val lat = poiLatitude ?: return null
    val lon = poiLongitude ?: return null
    val stableId = sourceId?.takeIf { it.isNotBlank() }
        ?: "${roadRef}_${lat}_${lon}"

    if (enricher != null && zonePoints == null && sourceId != null) {
        // Asynchronously trigger lazy polygon enrichment when displayed on map
        defaultEnricherScope.launch {
            try {
                enricher.enrichAndCachePolygon(this@toMapPoi, providerId)
            } catch (_: Exception) {}
        }
    }

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

fun TrafficInfo.toMapPois(enricher: TrafficPolygonEnricher? = null): List<Poi> =
    events.mapNotNull { it.toMapPoi(providerId, enricher) }

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
