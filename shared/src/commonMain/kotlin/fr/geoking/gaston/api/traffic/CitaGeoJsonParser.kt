package fr.geoking.gaston.api.traffic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses CITA [niveau_service_geojson](https://cita.lu/geojson/niveau_service_geojson.json)
 * into [TrafficEvent]s (polygon bbox + service level).
 */
object CitaGeoJsonParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): List<TrafficEvent> {
        val root = try {
            json.parseToJsonElement(body).jsonObject
        } catch (_: Exception) {
            return emptyList()
        }
        val features = root["features"]?.jsonArray ?: return emptyList()
        val out = mutableListOf<TrafficEvent>()
        for (el in features) {
            val feature = el as? JsonObject ?: continue
            val id = feature["id"]?.jsonPrimitive?.contentOrNull
            val props = feature["properties"] as? JsonObject ?: continue
            val geom = feature["geometry"] as? JsonObject ?: continue
            val bbox = bboxFromGeometry(geom) ?: continue
            val road = props["road"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: "—"
            val direction = props["direction"]?.jsonPrimitive?.contentOrNull
                ?: props["name"]?.jsonPrimitive?.contentOrNull
            val desc = props["service_level_desc"]?.jsonPrimitive?.contentOrNull ?: ""
            val level = props["service_level"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            val severity = levelToSeverity(level, desc)
            val message = buildString {
                if (desc.isNotBlank()) append(desc)
                val name = props["name"]?.jsonPrimitive?.contentOrNull
                if (name != null && name.isNotBlank()) {
                    if (isNotEmpty()) append(" — ")
                    append(name)
                }
            }.ifBlank { null }
            val ts = props["timestamp"]?.jsonPrimitive?.contentOrNull
            out.add(
                TrafficEvent(
                    roadRef = road,
                    direction = direction,
                    severity = severity,
                    message = message,
                    travelTimeSeconds = null,
                    bbox = bbox,
                    sourceId = id,
                    updatedAt = CitaDatexParser.parseMeasurementTime(ts)
                )
            )
        }
        return out
    }

    private fun levelToSeverity(level: Int?, descLower: String): TrafficSeverity {
        val d = descLower.lowercase()
        return when {
            d.contains("jam") || d.contains("bouchon") -> TrafficSeverity.Congestion
            d.contains("congest") || d.contains("dense") -> TrafficSeverity.Congestion
            d.contains("fluid") || d.contains("free") -> TrafficSeverity.Normal
            d.contains("closed") || d.contains("fermé") -> TrafficSeverity.Closure
            level == null -> TrafficSeverity.Unknown
            level >= 4 -> TrafficSeverity.Congestion
            level == 3 -> TrafficSeverity.Congestion
            level <= 1 -> TrafficSeverity.Normal
            level == 2 -> TrafficSeverity.Congestion
            else -> TrafficSeverity.Unknown
        }
    }

    private fun bboxFromGeometry(geom: JsonObject): Bbox? {
        val type = geom["type"]?.jsonPrimitive?.contentOrNull ?: return null
        val coords = geom["coordinates"] ?: return null
        return when (type) {
            "Polygon" -> ringToBbox(coords.jsonArray.getOrNull(0) as? JsonArray)
            "MultiPolygon" -> {
                var acc: Bbox? = null
                for (poly in coords.jsonArray) {
                    val polyArr = poly as? JsonArray ?: continue
                    val ring = polyArr.getOrNull(0) as? JsonArray ?: continue
                    val b = ringToBbox(ring) ?: continue
                    acc = if (acc == null) b else acc.union(b)
                }
                acc
            }
            else -> null
        }
    }

    private fun ringToBbox(ring: JsonArray?): Bbox? {
        if (ring == null || ring.isEmpty()) return null
        var latMin = Double.POSITIVE_INFINITY
        var latMax = Double.NEGATIVE_INFINITY
        var lonMin = Double.POSITIVE_INFINITY
        var lonMax = Double.NEGATIVE_INFINITY
        for (pt in ring) {
            val pair = pt as? JsonArray ?: continue
            val lon = pair.getOrNull(0)?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: continue
            val lat = pair.getOrNull(1)?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: continue
            if (lat < latMin) latMin = lat
            if (lat > latMax) latMax = lat
            if (lon < lonMin) lonMin = lon
            if (lon > lonMax) lonMax = lon
        }
        if (latMin.isInfinite()) return null
        return Bbox(latMin = latMin, lonMin = lonMin, latMax = latMax, lonMax = lonMax)
    }

    private fun Bbox.union(o: Bbox): Bbox = Bbox(
        latMin = minOf(latMin, o.latMin),
        lonMin = minOf(lonMin, o.lonMin),
        latMax = maxOf(latMax, o.latMax),
        lonMax = maxOf(lonMax, o.lonMax)
    )
}
