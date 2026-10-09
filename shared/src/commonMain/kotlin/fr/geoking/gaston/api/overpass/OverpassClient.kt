package fr.geoking.gaston.api.overpass

import fr.geoking.gaston.shared.network.NetworkException
import fr.geoking.gaston.shared.network.RateLimitTracker
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.cos
import kotlin.math.PI

internal const val OVERPASS_USER_AGENT = "gaston-App (contact@geoking.fr)"

/**
 * Client for the [Overpass API](https://wiki.openstreetmap.org/wiki/Overpass_API) (OpenStreetMap).
 * Queries nodes/ways by OSM tags (e.g. amenity=toilets, amenity=drinking_water).
 * No API key required. Use responsibly (public instances enforce slots + cool-down;
 * this client serializes calls, spaces them, and honors HTTP 429 without flooding).
 */
open class OverpassClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://overpass-api.de/api/interpreter",
    /** Minimum gap between Overpass network calls (anti-flood). 0 disables spacing. */
    private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
) {
    companion object {
        /** Stay under ~1 req/s on shared public instances (fair-use). */
        const val DEFAULT_MIN_INTERVAL_MS = 1_000L
        private const val DEFAULT_429_COOLDOWN_SEC = 60L
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var lastRequestEndMs: Long = 0L

    /**
     * Fetch POI nodes matching the given OSM amenity tag values in the bounding box.
     * @param latitude Center latitude
     * @param longitude Center longitude
     * @param radiusKm Search radius in km
     * @param amenityValues OSM "amenity" tag values, e.g. "toilets", "drinking_water"
     * @param limit Max elements (Overpass may return more; we trim)
     */
    suspend fun queryNodes(
        latitude: Double,
        longitude: Double,
        radiusKm: Int = 5,
        amenityValues: Set<String>,
        limit: Int = 100
    ): List<OverpassElement> = queryNodesWithTagFilters(
        latitude, longitude, radiusKm,
        listOf("amenity" to amenityValues),
        limit
    )

    /**
     * Fetch POI nodes matching multiple OSM tag key/value filters (e.g. amenity + tourism).
     * Builds a union of node[key=value] for each (key, values) pair.
     */
    suspend fun queryNodesWithTagFilters(
        latitude: Double,
        longitude: Double,
        radiusKm: Int = 5,
        tagFilters: List<Pair<String, Set<String>>>,
        limit: Int = 100,
        minLat: Double? = null,
        maxLat: Double? = null,
        minLng: Double? = null,
        maxLng: Double? = null
    ): List<OverpassElement> {
        val flat = tagFilters.map { (key, values) -> values.map { v -> key to v } }.flatten()
        if (flat.isEmpty()) return emptyList()
        val bbox = if (minLat != null && maxLat != null && minLng != null && maxLng != null) {
            "$minLat,$minLng,$maxLat,$maxLng"
        } else {
            val deltaLat = radiusKm / 111.0
            val deltaLng = radiusKm / (111.0 * cos(latitude * PI / 180)).coerceAtLeast(0.01)
            val south = latitude - deltaLat
            val north = latitude + deltaLat
            val west = longitude - deltaLng
            val east = longitude + deltaLng
            "$south,$west,$north,$east"
        }
        val unionParts = flat.joinToString("\n") { (key, value) ->
            """  node["$key"="$value"]($bbox);"""
        }
        val query = """
            [out:json][timeout:25];
            (
            $unionParts
            );
            out body qt ${limit.coerceIn(1, 500)};
        """.trimIndent()
        return parseElements(executeQuery(query), nodesOnly = true)
    }

    /**
     * Fetch POI nodes and ways matching the given tag filters (e.g. amenity=truck_stop, highway=rest_area).
     * Ways are returned with a representative point (center). Use for categories that are often mapped as ways.
     */
    open suspend fun queryNodesAndWaysWithTagFilters(
        latitude: Double,
        longitude: Double,
        radiusKm: Int = 5,
        tagFilters: List<Pair<String, Set<String>>>,
        limit: Int = 100,
        minLat: Double? = null,
        maxLat: Double? = null,
        minLng: Double? = null,
        maxLng: Double? = null
    ): List<OverpassElement> {
        val flat = tagFilters.map { (key, values) -> values.map { v -> key to v } }.flatten()
        if (flat.isEmpty()) return emptyList()
        val bbox = if (minLat != null && maxLat != null && minLng != null && maxLng != null) {
            "$minLat,$minLng,$maxLat,$maxLng"
        } else {
            val deltaLat = radiusKm / 111.0
            val deltaLng = radiusKm / (111.0 * cos(latitude * PI / 180)).coerceAtLeast(0.01)
            val south = latitude - deltaLat
            val north = latitude + deltaLat
            val west = longitude - deltaLng
            val east = longitude + deltaLng
            "$south,$west,$north,$east"
        }
        val unionParts = flat.joinToString("\n") { (key, value) ->
            """  node["$key"="$value"]($bbox);  way["$key"="$value"]($bbox);"""
        }
        val query = """
            [out:json][timeout:25];
            (
            $unionParts
            );
            out center qt ${limit.coerceIn(1, 500)};
        """.trimIndent()
        return parseElements(executeQuery(query), nodesOnly = false)
    }

    /**
     * Fetch highway ways near a point for road-class map-matching (no full geometry).
     * Uses Overpass `around:` + `out center tags` so ways include a representative center.
     */
    open suspend fun queryHighwayWaysAround(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = 40,
        limit: Int = 50,
    ): List<OverpassElement> {
        val r = radiusMeters.coerceIn(10, 200)
        val query = """
            [out:json][timeout:15];
            way(around:$r,$latitude,$longitude)["highway"];
            out center tags qt ${limit.coerceIn(1, 100)};
        """.trimIndent()
        return parseElements(executeQuery(query), nodesOnly = false)
    }

    /**
     * Speed cameras in a bbox plus enforcement relations (from/to node coords when available).
     * Also returns nearby highway ways with lightweight geometry endpoints for forward/backward.
     */
    open suspend fun querySpeedCamerasInBbox(
        minLat: Double,
        minLon: Double,
        maxLat: Double,
        maxLon: Double,
        limit: Int = 300,
    ): SpeedCameraOverpassBundle {
        val bbox = "$minLat,$minLon,$maxLat,$maxLon"
        val query = """
            [out:json][timeout:25];
            (
              node["highway"="speed_camera"]($bbox);
            )->.cams;
            .cams out body;
            rel(bn.cams)["type"="enforcement"]->.enf;
            .enf out;
            node(r.enf)->.enfnodes;
            .enfnodes out body;
            way(around.cams:40)["highway"]->.ways;
            .ways out geom qt ${limit.coerceIn(1, 200)};
        """.trimIndent()
        return parseSpeedCameraBundle(executeQuery(query), limit)
    }

    /**
     * Public Overpass instances reject generic OkHttp/Ktor User-Agents with HTTP 406
     * (Apache "Not Acceptable"). Identify the app like Nominatim requests.
     *
     * Serializes requests, spaces them by [minIntervalMs], and on HTTP 429 records a
     * host cooldown (from `cooldown N s remaining` / Retry-After / body / 60s default)
     * so further calls fail fast without flooding the public instance.
     */
    protected suspend fun executeQuery(query: String): String {
        throwIfRateLimited()
        return mutex.withLock {
            throwIfRateLimited()
            maybeDelayForMinInterval()
            val response = try {
                client.submitForm(
                    url = baseUrl,
                    formParameters = Parameters.build {
                        append("data", query)
                    }
                ) {
                    header(HttpHeaders.UserAgent, OVERPASS_USER_AGENT)
                    header(HttpHeaders.Accept, "*/*")
                }
            } catch (e: NetworkException) {
                if (e.httpCode == 429) {
                    recordCooldownFromText(e.message)
                }
                lastRequestEndMs = System.currentTimeMillis()
                throw e
            }
            val body = response.bodyAsText()
            lastRequestEndMs = System.currentTimeMillis()
            when (response.status.value) {
                200 -> body
                429 -> {
                    val sec = resolveCooldownSeconds(
                        body = body,
                        retryAfter = response.headers[HttpHeaders.RetryAfter],
                    )
                    RateLimitTracker.recordRateLimit(
                        hostOrUrl = baseUrl,
                        retryAfterHeader = sec.toString(),
                        responseBody = body,
                    )
                    throw NetworkException(
                        429,
                        "Overpass API rate limit (cooldown $sec s remaining)",
                    )
                }
                else -> throw NetworkException(
                    response.status.value,
                    "Overpass API error: ${body.take(500)}",
                )
            }
        }
    }

    private fun throwIfRateLimited() {
        if (!RateLimitTracker.isRateLimited(baseUrl)) return
        throw NetworkException(429, RateLimitTracker.cooldownMessage(baseUrl))
    }

    private suspend fun maybeDelayForMinInterval() {
        if (minIntervalMs <= 0L || lastRequestEndMs <= 0L) return
        val waitMs = lastRequestEndMs + minIntervalMs - System.currentTimeMillis()
        if (waitMs > 0L) delay(waitMs)
    }

    private fun recordCooldownFromText(text: String?) {
        val sec = RateLimitTracker.parseCooldownSecondsFromText(text) ?: DEFAULT_429_COOLDOWN_SEC
        RateLimitTracker.recordRateLimit(
            hostOrUrl = baseUrl,
            retryAfterHeader = sec.toString(),
            responseBody = text,
        )
    }

    private fun resolveCooldownSeconds(body: String, retryAfter: String?): Long {
        retryAfter?.trim()?.toLongOrNull()?.takeIf { it > 0 }?.let { return it }
        RateLimitTracker.parseCooldownSecondsFromText(body)?.let { return it }
        return DEFAULT_429_COOLDOWN_SEC
    }

    private fun parseSpeedCameraBundle(body: String, limit: Int): SpeedCameraOverpassBundle {
        val root = json.parseToJsonElement(body).jsonObject
        val arr = root["elements"]?.jsonArray ?: return SpeedCameraOverpassBundle()
        val nodes = mutableMapOf<Long, OverpassElement>()
        val cameras = mutableListOf<OverpassElement>()
        val ways = mutableListOf<OverpassWayGeom>()
        val relations = mutableListOf<OverpassEnforcementRelation>()

        for (el in arr) {
            val obj = el.jsonObject
            val type = obj["type"]?.jsonPrimitive?.content ?: continue
            val id = obj["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: continue
            val tags = (obj["tags"] as? JsonObject)?.mapValues { (_, v) ->
                (v as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
            } ?: emptyMap()
            when (type) {
                "node" -> {
                    val lat = obj["lat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: continue
                    val lon = obj["lon"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: continue
                    val node = OverpassElement(id = id, lat = lat, lon = lon, tags = tags)
                    nodes[id] = node
                    if (tags["highway"] == "speed_camera") {
                        cameras.add(node)
                    }
                }
                "way" -> {
                    val geometry = obj["geometry"]?.jsonArray?.mapNotNull { pt ->
                        val p = pt.jsonObject
                        val lat = p["lat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                        val lon = p["lon"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                        lat to lon
                    }.orEmpty()
                    if (geometry.size >= 2) {
                        ways.add(OverpassWayGeom(id = id, tags = tags, points = geometry))
                    }
                }
                "relation" -> {
                    if (tags["type"] != "enforcement") continue
                    val members = obj["members"]?.jsonArray?.mapNotNull { m ->
                        val mo = m.jsonObject
                        val role = mo["role"]?.jsonPrimitive?.content.orEmpty()
                        val ref = mo["ref"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@mapNotNull null
                        val mType = mo["type"]?.jsonPrimitive?.content.orEmpty()
                        OverpassRelationMember(type = mType, ref = ref, role = role)
                    }.orEmpty()
                    relations.add(OverpassEnforcementRelation(id = id, tags = tags, members = members))
                }
            }
        }

        return SpeedCameraOverpassBundle(
            cameras = cameras.take(limit),
            nodesById = nodes,
            ways = ways,
            enforcementRelations = relations,
        )
    }

    private fun parseElements(body: String, nodesOnly: Boolean = true): List<OverpassElement> {
        val root = json.parseToJsonElement(body).jsonObject
        val elements = root["elements"] ?: return emptyList()
        val arr = elements.jsonArray
        return arr.mapNotNull { el ->
            val obj = el.jsonObject
            val type = obj["type"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val id = obj["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@mapNotNull null
            val tags = (obj["tags"] as? JsonObject)?.mapValues { (_, v) -> (v as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "" } ?: emptyMap()
            when (type) {
                "node" -> {
                    val lat = obj["lat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                    val lon = obj["lon"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                    OverpassElement(id = id, lat = lat, lon = lon, tags = tags)
                }
                "way" -> if (nodesOnly) null else {
                    val center = obj["center"]?.jsonObject
                    val lat = center?.get("lat")?.jsonPrimitive?.content?.toDoubleOrNull()
                    val lon = center?.get("lon")?.jsonPrimitive?.content?.toDoubleOrNull()
                    val (wayLat, wayLon) = when {
                        lat != null && lon != null -> lat to lon
                        else -> obj["bounds"]?.jsonObject?.let { b ->
                            val minLat = b["minlat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@let null
                            val maxLat = b["maxlat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@let null
                            val minLon = b["minlon"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@let null
                            val maxLon = b["maxlon"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@let null
                            (minLat + maxLat) / 2.0 to (minLon + maxLon) / 2.0
                        } ?: return@mapNotNull null
                    }
                    OverpassElement(id = id, lat = wayLat, lon = wayLon, tags = tags)
                }
                else -> null
            }
        }
    }
}

data class OverpassRelationMember(
    val type: String,
    val ref: Long,
    val role: String,
)

data class OverpassEnforcementRelation(
    val id: Long,
    val tags: Map<String, String>,
    val members: List<OverpassRelationMember>,
)

data class OverpassWayGeom(
    val id: Long,
    val tags: Map<String, String>,
    val points: List<Pair<Double, Double>>,
) {
    fun bearingDegrees(): Double? {
        if (points.size < 2) return null
        val (lat1, lon1) = points.first()
        val (lat2, lon2) = points.last()
        return fr.geoking.gaston.aac.DangerZoneEvaluator.calculateBearing(lat1, lon1, lat2, lon2)
    }

    fun center(): Pair<Double, Double> {
        val lat = points.map { it.first }.average()
        val lon = points.map { it.second }.average()
        return lat to lon
    }
}

data class SpeedCameraOverpassBundle(
    val cameras: List<OverpassElement> = emptyList(),
    val nodesById: Map<Long, OverpassElement> = emptyMap(),
    val ways: List<OverpassWayGeom> = emptyList(),
    val enforcementRelations: List<OverpassEnforcementRelation> = emptyList(),
)

data class OverpassElement(
    val id: Long,
    val lat: Double,
    val lon: Double,
    val tags: Map<String, String>
) {
    fun amenity(): String? = tags["amenity"]
    fun tourism(): String? = tags["tourism"]
    fun highway(): String? = tags["highway"]
    fun name(lang: String? = null): String? = lang?.let { tags["name:$it"] } ?: tags["name"]
    fun street(): String? = tags["addr:street"] ?: tags["street"]
    fun address(): String? {
        val st = street()
        val house = tags["addr:housenumber"] ?: tags["housenumber"]
        val postcode = tags["addr:postcode"] ?: tags["postcode"]
        val city = tags["addr:city"] ?: tags["addr:place"] ?: tags["city"] ?: tags["place"]
        val suburb = tags["addr:suburb"] ?: tags["suburb"]

        val streetPart = st?.let { house?.let { h -> "$h $it" } ?: it }
        val cityPart = listOfNotNull(postcode, city).joinToString(" ").ifBlank { null }

        return listOfNotNull(streetPart, suburb, cityPart)
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .ifBlank { tags["address"] }
    }
    fun openingHours(): String? = tags["opening_hours"]
    fun cuisine(lang: String? = null): String? = lang?.let { tags["cuisine:$it"] } ?: tags["cuisine"]
    fun brand(lang: String? = null): String? = lang?.let { tags["brand:$it"] } ?: tags["brand"]
    fun operator(lang: String? = null): String? = lang?.let { tags["operator:$it"] } ?: tags["operator"]
}
