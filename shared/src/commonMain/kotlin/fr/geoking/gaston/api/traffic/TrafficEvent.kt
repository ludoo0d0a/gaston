package fr.geoking.gaston.api.traffic

import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.shared.datetime.DateTimeUtils

/**
 * Severity of a traffic event, normalized across providers.
 */
enum class TrafficSeverity {
    Normal,
    Congestion,
    Accident,
    Roadworks,
    Closure,
    Unknown
}

/**
 * Normalized traffic event or segment that any provider produces.
 * Used for map overlay, route screen, and voice.
 */
data class TrafficEvent(
    /** Road reference (e.g. "A6", "A13"). */
    val roadRef: String,
    /** Direction or stretch description, if available. */
    val direction: String? = null,
    val severity: TrafficSeverity = TrafficSeverity.Unknown,
    /** Free-text message from the source. */
    val message: String? = null,
    /** Travel time in seconds for the segment, if available. */
    val travelTimeSeconds: Int? = null,
    /** Bounding box (latMin, lonMin, latMax, lonMax) for map overlay, optional. */
    val bbox: Bbox? = null,
    /** Provider-specific event id. */
    val sourceId: String? = null,
    /** Last update timestamp from source, if available. */
    val updatedAt: Long? = null,
    /** Latitude of the primary POI location (first point of the zone/event). */
    val latitude: Double? = null,
    /** Longitude of the primary POI location (first point of the zone/event). */
    val longitude: Double? = null,
    /** Coordinates defining the zone if 2 points are different; null if points are identical or single point. */
    val zonePoints: List<Pair<Double, Double>>? = null,
    /** Raw XML snippet of the situationRecord element if from DATEX II feed. */
    val rawXml: String? = null,
    /** Raw JSON string or source data if from JSON feed. */
    val rawData: String? = null
) {
    /** Resolved POI latitude, using [latitude] or falling back to bbox center. */
    val poiLatitude: Double?
        get() = latitude ?: bbox?.let { (it.latMin + it.latMax) / 2.0 }

    /** Resolved POI longitude, using [longitude] or falling back to bbox center. */
    val poiLongitude: Double?
        get() = longitude ?: bbox?.let { (it.lonMin + it.lonMax) / 2.0 }
}

/**
 * Converts a [TrafficEvent] into a standard [Poi] object.
 */
fun TrafficEvent.toPoi(sourceName: String = "Traffic"): Poi {
    val resolvedSource = when {
        roadRef == "TomTom" -> "TomTom Traffic"
        sourceId?.contains("cita", ignoreCase = true) == true || roadRef == "CITA" -> "CITA Traffic"
        sourceId?.contains("action_b", ignoreCase = true) == true || sourceId?.contains("action_c", ignoreCase = true) == true -> "TIPI"
        sourceId?.contains("bison_fute", ignoreCase = true) == true -> "Bison Futé"
        sourceName.isNotBlank() && sourceName != "Traffic" -> sourceName
        else -> "Bison Futé / TIPI"
    }
    val poiLat = poiLatitude ?: 0.0
    val poiLon = poiLongitude ?: 0.0
    val titleName = roadRef + (direction?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
    val addr = message?.takeIf { it.isNotBlank() } ?: direction ?: roadRef
    val payload = rawXml ?: rawData ?: message ?: ""
    val rawMap = buildMap<String, String> {
        if (payload.isNotBlank()) {
            put(resolvedSource, payload)
        }
        put("traffic_severity", severity.name)
        direction?.takeIf { it.isNotBlank() }?.let { put("traffic_direction", it) }
        roadRef.takeIf { it.isNotBlank() }?.let { put("traffic_road_ref", it) }
    }
    val updateTimeMap = updatedAt?.let {
        mapOf(resolvedSource to kotlin.time.Instant.fromEpochMilliseconds(it).toString())
    }

    return Poi(
        id = sourceId ?: "traffic_${roadRef}_${poiLat}_${poiLon}",
        name = titleName,
        address = addr,
        latitude = poiLat,
        longitude = poiLon,
        poiCategory = PoiCategory.Traffic,
        source = resolvedSource,
        sourceUpdates = updateTimeMap,
        rawSourceData = rawMap,
    )
}

/**
 * Bounding box: (latMin, lonMin, latMax, lonMax).
 */
data class Bbox(
    val latMin: Double,
    val lonMin: Double,
    val latMax: Double,
    val lonMax: Double
) {
    fun contains(lat: Double, lon: Double): Boolean =
        lat in latMin..latMax && lon in lonMin..lonMax
}
