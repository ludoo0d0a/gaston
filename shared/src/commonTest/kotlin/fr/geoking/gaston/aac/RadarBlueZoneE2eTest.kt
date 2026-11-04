package fr.geoking.gaston.aac

import fr.geoking.gaston.api.overpass.OverpassClient
import fr.geoking.gaston.api.radars.FranceRadarsClient
import fr.geoking.gaston.road.RoadGeometryRepository
import fr.geoking.gaston.road.RoadPolyline
import fr.geoking.gaston.shared.network.RateLimitTracker
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * End-to-end style checks of the radar → Overpass → blue corridor pipeline, with the network
 * replaced by a single [MockEngine] that serves both the radar CSV and the Overpass responses.
 *
 * Scenario: a fixed radar (130 km/h) sits on a north–south motorway at 48.8500 / 2.3500.
 */
class RadarBlueZoneE2eTest {

    private val radarHost = "radars.test"
    private val overpassHost = "overpass-api.de"

    private val radarCsv = """
        Numéro de radar;Type de radar;Date de mise en service;VMA ;Latitude; Longitude
        12014;FIXE;01/01/2012 00:00;130;+48.8500;+2.3500
    """.trimIndent()

    /** Motorway running south → north through the radar, 48.8300 → 48.8700 (~4.4 km). */
    private val motorwayLatitudes = (0..8).map { 48.8300 + it * 0.0050 }
    private val overpassRoadBody: String = buildString {
        append("""{"elements":[{"type":"way","id":101,"tags":{"highway":"motorway","name":"A1"},"geometry":[""")
        append(motorwayLatitudes.joinToString(",") { """{"lat":$it,"lon":2.35}""" })
        append("]}]}")
    }

    /** Counts every request by host and lets a test script the Overpass answers. */
    private inner class FakeNetwork(
        private val csv: String = radarCsv,
    ) {
        var overpassCalls = 0
        var radarCalls = 0
        var overpassResponse: (Int) -> Pair<HttpStatusCode, Map<String, String>> = {
            HttpStatusCode.OK to emptyMap()
        }

        val engine = MockEngine { request ->
            if (request.url.host == radarHost) {
                radarCalls++
                respond(csv, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/csv"))
            } else {
                overpassCalls++
                val (status, extraHeaders) = overpassResponse(overpassCalls)
                val body = if (status == HttpStatusCode.OK) overpassRoadBody else "rate limited"
                val responseHeaders = io.ktor.http.Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    extraHeaders.forEach { (k, v) -> append(k, v) }
                }
                respond(body, status, responseHeaders)
            }
        }

        val overpass = OverpassClient(HttpClient(engine), minIntervalMs = 0L)
        val radars = FranceRadarsClient(HttpClient(engine), csvUrl = "https://$radarHost/radars.csv")
        val repository = DangerZoneRepository(
            franceRadarsClient = radars,
            roadGeometryRepository = RoadGeometryRepository(overpass),
        )
    }

    @BeforeTest
    fun setUp() {
        RateLimitTracker.reset()
    }

    private fun blue(rings: List<List<Pair<Double, Double>>>, lat: Double, lon: Double) =
        rings.any { DangerZoneTriangle.pointInRing(lat, lon, it) }

    @Test
    fun radarZone_getsBlueCorridorAlongTheRoad_onBothSidesWhenDirectionUnknown() = runBlocking {
        val net = FakeNetwork()

        val rings = net.repository.roadCorridorRingsNear(48.8500, 2.3500, radiusKm = 8.0)

        assertTrue(rings.isNotEmpty(), "a blue corridor must be produced for the radar")
        // ~300 m south and ~300 m north of the radar, on the motorway centreline.
        assertTrue(blue(rings, 48.8473, 2.3500), "corridor should cover the road south of the radar")
        assertTrue(blue(rings, 48.8527, 2.3500), "corridor should cover the road north of the radar")
        // ~150 m east: off the road, outside the ~35 m half-width buffer.
        assertFalse(blue(rings, 48.8500, 2.3520), "corridor must not spill far off the road")
        assertEquals(1, net.overpassCalls, "one road query per road-class batch")
        assertEquals(1, net.radarCalls, "radar CSV fetched once")
    }

    @Test
    fun twoRadarsSameClass_shareOneOverpassRoadFetch() = runBlocking {
        // Second radar ~1.1 km north on the same motorway (same RoadNetworkClass).
        val net = FakeNetwork(
            csv = """
                Numéro de radar;Type de radar;Date de mise en service;VMA ;Latitude; Longitude
                12014;FIXE;01/01/2012 00:00;130;+48.8500;+2.3500
                12015;FIXE;01/01/2012 00:00;130;+48.8600;+2.3500
            """.trimIndent(),
        )

        val rings = net.repository.roadCorridorRingsNear(48.8500, 2.3500, radiusKm = 8.0)
        assertTrue(rings.isNotEmpty())
        assertEquals(1, net.overpassCalls, "same class → one Overpass road fetch for both radars")
    }

    @Test
    fun corridor_isUpstreamOnly_whenCameraMonitorsNorthbound() {
        // Camera facing north (bearing 0): vehicles approach from the south, so the blue zone
        // must lie upstream (south) and not downstream (north).
        val road = RoadPolyline(
            id = 101L,
            highwayType = "motorway",
            name = "A1",
            points = motorwayLatitudes.map { it to 2.35 },
        )
        val rings = RadarRoadCorridorCalculator.corridorRings(
            tipLat = 48.8500,
            tipLon = 2.3500,
            radiusMeters = 800.0,
            monitoredBearingDegrees = 0.0,
            bidirectional = false,
            roads = listOf(road),
        )
        assertTrue(rings.isNotEmpty())
        assertTrue(blue(rings, 48.8473, 2.3500), "upstream (south) must be blue")
        assertFalse(blue(rings, 48.8527, 2.3500), "downstream (north) must not be blue")
    }

    @Test
    fun repeatedMapRefresh_doesNotRequeryOverpass_forAZoneAlreadyEnriched() = runBlocking {
        val net = FakeNetwork()

        net.repository.roadCorridorRingsNear(48.8500, 2.3500)
        net.repository.roadCorridorRingsNear(48.8500, 2.3500)
        net.repository.roadCorridorRingsNear(48.8502, 2.3501)

        assertEquals(1, net.overpassCalls, "enriched zones must not trigger new Overpass calls")
    }

    @Test
    fun http429_opensCooldown_noRetryDuringCooldown_thenRecovers() = runBlocking {
        val net = FakeNetwork()
        net.overpassResponse = { call ->
            if (call == 1) HttpStatusCode.TooManyRequests to mapOf(HttpHeaders.RetryAfter to "2")
            else HttpStatusCode.OK to emptyMap()
        }

        // 1st call: Overpass answers 429 → synthetic blue ribbon still published for the map.
        val first = net.repository.roadCorridorRingsNear(48.8500, 2.3500)
        assertTrue(first.isNotEmpty(), "synthetic blue corridor must show while Overpass is down")
        assertEquals(1, net.overpassCalls)
        assertTrue(RateLimitTracker.isRateLimited(overpassHost), "host must be in cooldown")

        // 2nd call during cooldown: Overpass is NOT contacted again (RateLimitTracker short-circuit).
        val second = net.repository.roadCorridorRingsNear(48.8500, 2.3500)
        assertTrue(second.isNotEmpty())
        assertEquals(1, net.overpassCalls, "no Overpass request while cooling down")

        // After cooldown the zone is upgraded once to the real OSM road corridor.
        delay(2_100)
        val third = net.repository.roadCorridorRingsNear(48.8500, 2.3500)
        assertEquals(2, net.overpassCalls, "exactly one retry after the cooldown expires")
        assertTrue(blue(third, 48.8473, 2.3500), "corridor follows the road once Overpass answers again")
        assertFalse(blue(third, 48.8500, 2.3520), "upgraded corridor must not spill far off the road")
    }

    @Test
    fun overpassClient_spacesConsecutiveQueries_byMinInterval() = runBlocking {
        val net = FakeNetwork()
        val spaced = OverpassClient(HttpClient(net.engine), minIntervalMs = 250L)

        val start = TimeSource.Monotonic.markNow()
        repeat(3) { spaced.queryRoadWaysAround(48.85, 2.35, radiusMeters = 100, highwayRegex = "motorway") }
        val elapsedMs = start.elapsedNow().inWholeMilliseconds

        assertEquals(3, net.overpassCalls)
        assertTrue(elapsedMs >= 500L, "3 queries with 250 ms spacing must take >= 500 ms, took $elapsedMs ms")
    }

    @Test
    fun radarCsv_isFetchedOnce_andServedFromMemoryWithin24h() = runBlocking {
        val net = FakeNetwork()

        net.radars.getRecordsNear(48.85, 2.35, radiusKm = 10.0)
        net.radars.getRecordsNear(48.85, 2.35, radiusKm = 10.0)

        assertEquals(1, net.radarCalls, "radar CSV must be cached (24 h TTL) between calls")
    }
}
