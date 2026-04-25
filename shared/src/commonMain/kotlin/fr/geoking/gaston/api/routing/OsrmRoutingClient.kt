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
    ): RouteResult? {
        // OSRM expects coordinates as lon,lat
        val coords = "${originLon},${originLat};${destLon},${destLat}"
        val profileSegment = profile?.takeIf { it.isNotBlank() } ?: defaultProfile
        val url = "$baseUrl/$profileSegment/$coords?overview=full&geometries=polyline"
        val response = client.get(url)
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "OSRM error: $body")
        }
        val root = json.parseToJsonElement(body).jsonObject
        val code = root["code"]?.jsonPrimitive?.content
        if (code != "Ok") return null
        val routes = root["routes"]?.jsonArray ?: return null
        val route = routes.getOrNull(0)?.jsonObject ?: return null
        val geometry = route["geometry"]?.jsonPrimitive?.content ?: return null
        val distance = route["distance"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
        val points = PolylineUtils.decode(geometry)
        return RouteResult(points = points, distanceMeters = distance)
    }
}
