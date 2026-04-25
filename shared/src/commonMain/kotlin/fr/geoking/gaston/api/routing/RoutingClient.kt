package fr.geoking.gaston.api.routing

/**
 * Client for fetching a route between two points.
 * Implementations may use OSRM, GraphHopper, or other public routing APIs.
 */
interface RoutingClient {
    /**
     * Get a route from (originLat, originLon) to (destLat, destLon).
     * OSRM uses longitude,latitude order in the URL.
     * [profile] optionally selects the routing profile (e.g. "driving", "cycling").
     * Public OSRM typically offers driving, driving-traffic, walking, cycling; truck/motorcycle
     * require a self-hosted OSRM or GraphHopper backend. Returns null if the request fails or no route is found.
     */
    suspend fun getRoute(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        profile: String? = null
    ): RouteResult?
}
