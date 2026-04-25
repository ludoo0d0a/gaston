package fr.geoking.gaston.api.traffic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Global traffic incidents via TomTom Traffic API v5. Used as factory fallback for regions without a dedicated feed.
 * [apiKey] may be empty; [getTraffic] then returns null.
 */
class TomTomTrafficProvider(
    private val client: TomTomTrafficClient,
    private val apiKey: String
) : TrafficProvider {

    override val enabled: Boolean = false

    override suspend fun getTraffic(request: TrafficRequest): TrafficInfo? {
        if (apiKey.isBlank()) return null
        val (la0, lo0, la1, lo1) = bboxFromRequest(request) ?: return null
        val body = client.fetchIncidents(apiKey, la0, lo0, la1, lo1) ?: return null
        val events = TomTomIncidentParser.parse(body)
        return TrafficInfo(events = events, providerId = PROVIDER_ID)
    }

    private fun bboxFromRequest(request: TrafficRequest): Quad? = when (request) {
        is TrafficRequest.Bbox -> Quad(
            request.latMin.coerceAtMost(request.latMax),
            request.lonMin.coerceAtMost(request.lonMax),
            request.latMax.coerceAtLeast(request.latMin),
            request.lonMax.coerceAtLeast(request.lonMin)
        )
        is TrafficRequest.Route -> {
            val pts = request.points
            if (pts.isEmpty()) return null
            val lats = pts.map { it.first }
            val lons = pts.map { it.second }
            Quad(lats.minOrNull()!!, lons.minOrNull()!!, lats.maxOrNull()!!, lons.maxOrNull()!!)
        }
    }

    private data class Quad(val latMin: Double, val lonMin: Double, val latMax: Double, val lonMax: Double)

    companion object {
        const val PROVIDER_ID = "tomtom"
    }
}

private object TomTomIncidentParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): List<TrafficEvent> {
        val root = try {
            json.parseToJsonElement(body).jsonObject
        } catch (_: Exception) {
            return emptyList()
        }
        val incidents = root["incidents"]?.jsonArray ?: return emptyList()
        val out = mutableListOf<TrafficEvent>()
        for (el in incidents) {
            val feature = el as? JsonObject ?: continue
            val geom = feature["geometry"] as? JsonObject ?: continue
            val props = feature["properties"] as? JsonObject ?: continue
            val bbox = bboxFromGeometry(geom) ?: continue
            val id = props["id"]?.jsonPrimitive?.contentOrNull
            val icon = props["iconCategory"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            val delay = props["magnitudeOfDelay"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
            val desc = firstEventDescription(props)
            val severity = severityFrom(icon, delay)
            out.add(
                TrafficEvent(
                    roadRef = "TomTom",
                    direction = null,
                    severity = severity,
                    message = desc,
                    travelTimeSeconds = null,
                    bbox = bbox,
                    sourceId = id,
                    updatedAt = null
                )
            )
        }
        return out
    }

    private fun firstEventDescription(props: JsonObject): String? {
        val events = props["events"]?.jsonArray ?: return null
        for (e in events) {
            val o = e as? JsonObject ?: continue
            val d = o["description"]?.jsonPrimitive?.contentOrNull
            if (!d.isNullOrBlank()) return d
        }
        return null
    }

    private fun severityFrom(iconCategory: Int?, magnitudeOfDelay: Int): TrafficSeverity {
        if (iconCategory != null) {
            when (iconCategory) {
                8 -> return TrafficSeverity.Closure
                7, 9 -> return TrafficSeverity.Roadworks
                1 -> return TrafficSeverity.Accident
                6 -> return TrafficSeverity.Congestion
            }
        }
        return when {
            magnitudeOfDelay >= 2 -> TrafficSeverity.Congestion
            magnitudeOfDelay == 1 -> TrafficSeverity.Unknown
            else -> TrafficSeverity.Unknown
        }
    }

    private fun bboxFromGeometry(geom: JsonObject): Bbox? {
        return when (geom["type"]?.jsonPrimitive?.contentOrNull) {
            "Point" -> pointBbox(geom["coordinates"]?.jsonArray)
            "LineString" -> lineBbox(geom["coordinates"]?.jsonArray)
            "MultiLineString" -> {
                var acc: Bbox? = null
                val arr = geom["coordinates"]?.jsonArray ?: return null
                for (line in arr) {
                    val b = lineBbox(line as? JsonArray) ?: continue
                    acc = if (acc == null) b else acc.union(b)
                }
                acc
            }
            else -> null
        }
    }

    private fun pointBbox(coords: JsonArray?): Bbox? {
        if (coords == null || coords.size < 2) return null
        val lon = coords[0].jsonPrimitive.contentOrNull?.toDoubleOrNull() ?: return null
        val lat = coords[1].jsonPrimitive.contentOrNull?.toDoubleOrNull() ?: return null
        return Bbox(lat, lon, lat, lon)
    }

    private fun lineBbox(coords: JsonArray?): Bbox? {
        if (coords == null || coords.isEmpty()) return null
        var latMin = Double.POSITIVE_INFINITY
        var latMax = Double.NEGATIVE_INFINITY
        var lonMin = Double.POSITIVE_INFINITY
        var lonMax = Double.NEGATIVE_INFINITY
        for (pt in coords) {
            val pair = pt as? JsonArray ?: continue
            val lon = pair.getOrNull(0)?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: continue
            val lat = pair.getOrNull(1)?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: continue
            if (lat < latMin) latMin = lat
            if (lat > latMax) latMax = lat
            if (lon < lonMin) lonMin = lon
            if (lon > lonMax) lonMax = lon
        }
        if (latMin.isInfinite()) return null
        return Bbox(latMin, lonMin, latMax, lonMax)
    }

    private fun Bbox.union(o: Bbox): Bbox = Bbox(
        latMin = minOf(latMin, o.latMin),
        lonMin = minOf(lonMin, o.lonMin),
        latMax = maxOf(latMax, o.latMax),
        lonMax = maxOf(lonMax, o.lonMax)
    )
}
