package fr.geoking.gaston.api.routing

import fr.geoking.gaston.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * [RoutingClient] implementation using the public OSRM demo server.
 * No API key required. May be rate-limited under heavy use.
 * See https://project-osrm.org/docs/v5.24.0/api/
 *
 * [baseUrl] should be the base up to and including "/route/v1" (e.g. https://router.project-osrm.org/route/v1).
 * [profile] is used in the path: /route/v1/{profile}/{coords}. Public OSRM supports "driving", "driving-traffic",
 * "walking", "cycling". Truck/motorcycle routing requires a custom backend with matching profiles.
 */
class OsrmRoutingClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://router.project-osrm.org/route/v1",
    private val defaultProfile: String = "driving"
) : RoutingClient {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getRoute(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        profile: String?
    ): RouteResult? = getRoutes(originLat, originLon, destLat, destLon, profile, alternatives = false)
        .firstOrNull()

    /**
     * Returns up to [maxAlternatives]+1 routes (primary + OSRM alternatives).
     * Also requests a no-motorway variant when [includeAvoidMotorway] is true.
     */
    suspend fun getRoutes(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        profile: String? = null,
        alternatives: Boolean = true,
        maxAlternatives: Int = 2,
        includeAvoidMotorway: Boolean = true,
    ): List<RouteResult> {
        val profileSegment = profile?.takeIf { it.isNotBlank() } ?: defaultProfile
        val coords = "${originLon},${originLat};${destLon},${destLat}"
        val altParam = if (alternatives) "true" else "false"
        val url =
            "$baseUrl/$profileSegment/$coords?overview=full&geometries=polyline&alternatives=$altParam"
        val routes = fetchRoutes(url).take(maxAlternatives + 1).toMutableList()

        if (includeAvoidMotorway) {
            val avoidUrl =
                "$baseUrl/$profileSegment/$coords?overview=full&geometries=polyline&exclude=motorway"
            val avoid = runCatching { fetchRoutes(avoidUrl, throwOnError = false) }.getOrDefault(emptyList())
                .firstOrNull()
            if (avoid != null && routes.none { approxSameRoute(it, avoid) }) {
                routes.add(avoid)
            }
        }
        return routes
    }

    private suspend fun fetchRoutes(url: String, throwOnError: Boolean = true): List<RouteResult> {
        val response = client.get(url)
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            if (!throwOnError) return emptyList()
            throw NetworkException(response.status.value, "OSRM error: $body")
        }
        val root = json.parseToJsonElement(body).jsonObject
        val code = root["code"]?.jsonPrimitive?.content
        if (code != "Ok") return emptyList()
        val routes = root["routes"]?.jsonArray ?: return emptyList()
        return routes.mapNotNull { el ->
            val route = el.jsonObject
            val geometry = route["geometry"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val distance = route["distance"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            val duration = route["duration"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            val points = PolylineUtils.decode(geometry)
            RouteResult(points = points, distanceMeters = distance, durationSeconds = duration)
        }
    }

    private fun approxSameRoute(a: RouteResult, b: RouteResult): Boolean {
        if (kotlin.math.abs(a.distanceMeters - b.distanceMeters) < 200) return true
        val aFirst = a.points.firstOrNull() ?: return false
        val aLast = a.points.lastOrNull() ?: return false
        val bFirst = b.points.firstOrNull() ?: return false
        val bLast = b.points.lastOrNull() ?: return false
        return aFirst == bFirst && aLast == bLast &&
            kotlin.math.abs(a.distanceMeters - b.distanceMeters) < 500
    }
}
