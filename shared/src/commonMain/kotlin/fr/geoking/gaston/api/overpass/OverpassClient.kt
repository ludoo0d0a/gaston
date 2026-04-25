package fr.geoking.gaston.api.overpass

import fr.geoking.gaston.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.cos
import kotlin.math.PI

/**
 * Client for the [Overpass API](https://wiki.openstreetmap.org/wiki/Overpass_API) (OpenStreetMap).
 * Queries nodes/ways by OSM tags (e.g. amenity=toilets, amenity=drinking_water).
 * No API key required. Use responsibly (rate limit ~2 req/s on public instances).
 */
class OverpassClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://overpass-api.de/api/interpreter"
) {
    private val json = Json { ignoreUnknownKeys = true }

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
        limit: Int = 100
    ): List<OverpassElement> {
        val flat = tagFilters.map { (key, values) -> values.map { v -> key to v } }.flatten()
        if (flat.isEmpty()) return emptyList()
        val deltaLat = radiusKm / 111.0
        val deltaLng = radiusKm / (111.0 * cos(latitude * PI / 180)).coerceAtLeast(0.01)
        val south = latitude - deltaLat
        val north = latitude + deltaLat
        val west = longitude - deltaLng
        val east = longitude + deltaLng
        val bbox = "$south,$west,$north,$east"
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

        val response = client.submitForm(
            url = baseUrl,
            formParameters = Parameters.build {
                append("data", query)
            }
        )
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "Overpass API error: ${body.take(500)}")
        }
        return parseElements(body, nodesOnly = true)
    }

    /**
     * Fetch POI nodes and ways matching the given tag filters (e.g. amenity=truck_stop, highway=rest_area).
     * Ways are returned with a representative point (center). Use for categories that are often mapped as ways.
     */
    suspend fun queryNodesAndWaysWithTagFilters(
        latitude: Double,
        longitude: Double,
        radiusKm: Int = 5,
        tagFilters: List<Pair<String, Set<String>>>,
        limit: Int = 100
    ): List<OverpassElement> {
        val flat = tagFilters.map { (key, values) -> values.map { v -> key to v } }.flatten()
        if (flat.isEmpty()) return emptyList()
        val deltaLat = radiusKm / 111.0
        val deltaLng = radiusKm / (111.0 * cos(latitude * PI / 180)).coerceAtLeast(0.01)
        val south = latitude - deltaLat
        val north = latitude + deltaLat
        val west = longitude - deltaLng
        val east = longitude + deltaLng
        val bbox = "$south,$west,$north,$east"
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

        val response = client.submitForm(
            url = baseUrl,
            formParameters = Parameters.build {
                append("data", query)
            }
        )
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "Overpass API error: ${body.take(500)}")
        }
        return parseElements(body, nodesOnly = false)
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

data class OverpassElement(
    val id: Long,
    val lat: Double,
    val lon: Double,
    val tags: Map<String, String>
) {
    fun amenity(): String? = tags["amenity"]
    fun tourism(): String? = tags["tourism"]
    fun highway(): String? = tags["highway"]
    fun name(): String? = tags["name"]
    fun address(): String? = tags["addr:street"]?.let { street ->
        val house = tags["addr:housenumber"]
        val city = tags["addr:city"] ?: tags["addr:place"]
        listOfNotNull(house?.let { "$it $street" } ?: street, city).joinToString(", ")
    } ?: tags["address"]
    fun openingHours(): String? = tags["opening_hours"]
    fun cuisine(): String? = tags["cuisine"]
    fun brand(): String? = tags["brand"]
}
