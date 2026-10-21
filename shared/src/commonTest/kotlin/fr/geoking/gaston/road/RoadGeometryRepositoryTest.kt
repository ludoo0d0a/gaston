package fr.geoking.gaston.road

import fr.geoking.gaston.api.overpass.OverpassClient
import fr.geoking.gaston.api.overpass.OverpassWayGeom
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class RoadGeometryRepositoryTest {

    @Test
    fun fetchRoadsNear_fetchesAndCachesPolylines() = runBlocking {
        var queryCount = 0
        val mockClient = object : OverpassClient(
            HttpClient(MockEngine {
                respond("", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            }),
            minIntervalMs = 0L,
        ) {
            override suspend fun queryDrivableRoadWaysInBbox(
                minLat: Double,
                minLon: Double,
                maxLat: Double,
                maxLon: Double,
                limit: Int,
            ): List<OverpassWayGeom> {
                queryCount++
                return listOf(
                    OverpassWayGeom(
                        id = 999L,
                        tags = mapOf("highway" to "secondary", "name" to "Avenue Foch"),
                        points = listOf(48.87 to 2.29, 48.88 to 2.30),
                    )
                )
            }
        }

        val repository = RoadGeometryRepository(mockClient)

        val roads1 = repository.fetchRoadsNear(48.87, 2.29, radiusKm = 1.0)
        assertEquals(1, roads1.size)
        assertEquals(999L, roads1.first().id)
        assertEquals("secondary", roads1.first().highwayType)
        assertEquals("Avenue Foch", roads1.first().name)
        assertEquals(1, queryCount)

        // Second call near same location should hit cache and not invoke Overpass
        val roads2 = repository.fetchRoadsNear(48.8701, 2.2901, radiusKm = 0.5)
        assertEquals(1, roads2.size)
        assertEquals(1, queryCount)

        // Clearing cache and requesting again should query Overpass
        repository.clearCache()
        val roads3 = repository.fetchRoadsNear(48.87, 2.29, radiusKm = 1.0)
        assertEquals(1, roads3.size)
        assertEquals(2, queryCount)
    }
}
