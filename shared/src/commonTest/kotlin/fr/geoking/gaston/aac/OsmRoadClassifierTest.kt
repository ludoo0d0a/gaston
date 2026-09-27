package fr.geoking.gaston.aac

import fr.geoking.gaston.api.overpass.OverpassClient
import fr.geoking.gaston.api.overpass.OverpassElement
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OsmRoadClassifierTest {

    @Test
    fun prefersMotorwayOverTrunk() = runBlocking {
        val client = FakeOverpassClient(
            listOf(
                OverpassElement(1, 48.0, 2.0, mapOf("highway" to "trunk")),
                OverpassElement(2, 48.0, 2.0, mapOf("highway" to "motorway")),
            )
        )
        val classifier = OsmRoadClassifier(client, nowMs = { 1_000L })
        val ctx = classifier.classify(48.0, 2.0)
        assertEquals(RoadNetworkClass.Motorway, ctx.roadClass)
        assertTrue(ctx.isOnMotorway)
        assertEquals(RoadContextSource.Osm, ctx.source)
    }

    @Test
    fun fallsBackToVmaWhenOverpassEmpty() = runBlocking {
        val classifier = OsmRoadClassifier(FakeOverpassClient(emptyList()), nowMs = { 1_000L })
        val ctx = classifier.classify(48.0, 2.0, speedLimitKmH = 130)
        assertEquals(RoadNetworkClass.Motorway, ctx.roadClass)
        assertTrue(ctx.isOnMotorway)
        assertEquals(RoadContextSource.Vma, ctx.source)
    }

    @Test
    fun fallsBackToThoroughfareWhenOsmAndVmaUnavailable() = runBlocking {
        val classifier = OsmRoadClassifier(FakeOverpassClient(emptyList()), nowMs = { 1_000L })
        val ctx = classifier.classify(48.0, 2.0, thoroughfare = "Autoroute A7")
        assertTrue(ctx.isOnMotorway)
        assertEquals(RoadContextSource.Thoroughfare, ctx.source)
    }

    @Test
    fun unknownWhenAllSourcesFail() = runBlocking {
        val classifier = OsmRoadClassifier(
            FakeOverpassClient(emptyList(), throwError = true),
            nowMs = { 1_000L },
        )
        val ctx = classifier.classify(48.0, 2.0)
        assertEquals(RoadContext.Unknown, ctx)
        assertFalse(ctx.isOnMotorway)
    }

    @Test
    fun reusesCacheWithinDistanceAndTtl() = runBlocking {
        var calls = 0
        val client = object : OverpassClient(HttpClient()) {
            override suspend fun queryHighwayWaysAround(
                latitude: Double,
                longitude: Double,
                radiusMeters: Int,
                limit: Int,
            ): List<OverpassElement> {
                calls++
                return listOf(OverpassElement(1, latitude, longitude, mapOf("highway" to "motorway")))
            }
        }
        var now = 1_000L
        val classifier = OsmRoadClassifier(client, nowMs = { now })
        classifier.classify(48.0, 2.0)
        classifier.classify(48.001, 2.001) // ~0.15 km
        assertEquals(1, calls)
        now = 1_000L + OsmRoadClassifier.DEFAULT_CACHE_TTL_MS + 1
        classifier.classify(48.0, 2.0)
        assertEquals(2, calls)
    }

    private class FakeOverpassClient(
        private val ways: List<OverpassElement>,
        private val throwError: Boolean = false,
    ) : OverpassClient(HttpClient()) {
        override suspend fun queryHighwayWaysAround(
            latitude: Double,
            longitude: Double,
            radiusMeters: Int,
            limit: Int,
        ): List<OverpassElement> {
            if (throwError) error("overpass down")
            return ways
        }
    }
}
