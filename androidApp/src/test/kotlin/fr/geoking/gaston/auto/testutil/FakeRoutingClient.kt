package fr.geoking.gaston.auto.testutil

import fr.geoking.gaston.api.routing.RouteResult
import fr.geoking.gaston.api.routing.RoutingClient

class FakeRoutingClient : RoutingClient {
    override suspend fun getRoute(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        profile: String?,
    ): RouteResult? = null
}
