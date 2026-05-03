package fr.geoking.gaston.api.routing

import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.poi.PoiSearchRequest
import fr.geoking.gaston.poi.PoiSearchResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutePlannerTest {

    private class MockRoutingClient(val routeResult: RouteResult?) : RoutingClient {
        override suspend fun getRoute(
            originLat: Double,
            originLon: Double,
            destLat: Double,
            destLon: Double,
            profile: String?
        ): RouteResult? = routeResult
    }

    private class MockPoiProvider(val searchResults: Map<Pair<Double, Double>, List<Poi>>) : PoiProvider {
        override suspend fun getGasStations(latitude: Double, longitude: Double, viewport: MapViewport?): List<Poi> = emptyList()
        override fun searchFlow(request: PoiSearchRequest): Flow<PoiSearchResult> {
            val pois = searchResults[request.latitude to request.longitude] ?: emptyList()
            return flowOf(PoiSearchResult(pois = pois))
        }
    }

    @Test
    fun getStationsAlongRouteFlow_findsPoisAlongRoute() = runBlocking {
        val points = listOf(
            48.0 to 2.0,
            48.1 to 2.1,
            48.2 to 2.2
        )
        val routeResult = RouteResult(points, 20000.0)
        val routingClient = MockRoutingClient(routeResult)

        // Sampling interval will be around radius * 1.5.
        // With radius 5000, interval is 7500m.
        // 48.0, 2.0 to 48.1, 2.1 is ~13km.
        // So we expect sampling at start, somewhere in between, and end.

        val p1 = Poi("1", "Station 1", "Addr 1", 48.001, 2.001)
        val p2 = Poi("2", "Station 2", "Addr 2", 48.201, 2.201)

        val poiProvider = MockPoiProvider(mapOf(
            (48.0 to 2.0) to listOf(p1),
            (48.2 to 2.2) to listOf(p2)
        ))

        val planner = RoutePlanner(routingClient)
        val allStations = mutableListOf<Poi>()
        planner.getStationsAlongRouteFlow(48.0, 2.0, 48.2, 2.2, poiProvider, radiusMeters = 5000).collect {
            allStations.clear()
            allStations.addAll(it)
        }

        // Due to parallel execution, we just check the final result
        assertEquals(2, allStations.size)
        assertTrue(allStations.any { it.id == "1" })
        assertTrue(allStations.any { it.id == "2" })
    }

    @Test
    fun getStationsAlongRouteFlow_deduplicatesPois() = runBlocking {
        val points = listOf(
            48.0 to 2.0,
            48.001 to 2.001
        )
        val routeResult = RouteResult(points, 200.0)
        val routingClient = MockRoutingClient(routeResult)

        val p1 = Poi("1", "Station 1", "Addr 1", 48.0, 2.0)

        // Both sampled points (start and end) will return the same POI
        val poiProvider = MockPoiProvider(mapOf(
            (48.0 to 2.0) to listOf(p1),
            (48.001 to 2.001) to listOf(p1)
        ))

        val planner = RoutePlanner(routingClient)
        val allStations = mutableListOf<Poi>()
        planner.getStationsAlongRouteFlow(48.0, 2.0, 48.001, 2.001, poiProvider, radiusMeters = 5000).collect {
            allStations.clear()
            allStations.addAll(it)
        }

        assertEquals(1, allStations.size)
    }
}
