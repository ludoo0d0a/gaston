package fr.geoking.gaston.api.routex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for RoutexClient response parsing.
 * API returns items with keys: id, x (longitude), y (latitude), brand_id.
 */
class RoutexClientTest {

    @Test
    fun parseResults_rootArrayWithIdXyBrandId_parsesSites() {
        // Routex API format: id, x, y, brand_id (x=lng, y=lat)
        val body = """
            [
                {"id":"site-1","x":2.3522,"y":48.8566,"brand_id":"42"},
                {"id":"site-2","x":2.36,"y":48.86,"brand_id":"BP"}
            ]
        """.trimIndent()
        val sites = RoutexClient.parseResults(body)
        assertEquals(2, sites.size)

        val first = sites[0]
        assertEquals("site-1", first.id)
        assertEquals(48.8566, first.latitude)
        assertEquals(2.3522, first.longitude)
        assertEquals("42", first.brand)
        assertEquals("Gas station", first.name)

        val second = sites[1]
        assertEquals("site-2", second.id)
        assertEquals(48.86, second.latitude)
        assertEquals(2.36, second.longitude)
        assertEquals("BP", second.brand)
    }

    @Test
    fun parseResults_brandIdAsNumber_parsesAsString() {
        val body = """[{"id":"1","x":2.35,"y":48.85,"brand_id":123}]"""
        val sites = RoutexClient.parseResults(body)
        assertEquals(1, sites.size)
        assertEquals("123", sites[0].brand)
    }

    @Test
    fun parseResults_geoJsonFeatures_parsesSites() {
        val body = """
            {
                "features": [
                    {
                        "type": "Feature",
                        "geometry": {"type": "Point", "coordinates": [2.35, 48.85]},
                        "properties": {"name": "Station A", "address": "1 Rue X", "id": "geo-1"}
                    }
                ]
            }
        """.trimIndent()
        val sites = RoutexClient.parseResults(body)
        assertEquals(1, sites.size)
        assertEquals("geo-1", sites[0].id)
        assertEquals(48.85, sites[0].latitude)
        assertEquals(2.35, sites[0].longitude)
        assertEquals("Station A", sites[0].name)
        assertEquals("1 Rue X", sites[0].address)
    }

    @Test
    fun parseResults_emptyArray_returnsEmpty() {
        val sites = RoutexClient.parseResults("[]")
        assertTrue(sites.isEmpty())
    }

    @Test
    fun parseResults_objectWithResultsArray_parsesSites() {
        val body = """
            {"results": [{"id":"r1","x":1.0,"y":2.0,"brand_id":"B1"}]}
        """.trimIndent()
        val sites = RoutexClient.parseResults(body)
        assertEquals(1, sites.size)
        assertEquals("r1", sites[0].id)
        assertEquals(2.0, sites[0].latitude)
        assertEquals(1.0, sites[0].longitude)
    }

    @Test
    fun filterInBoundsAndLimit_keepsOnlySitesInBounds() {
        val sites = listOf(
            RoutexSite("1", "A", "", 48.0, 2.0, null),
            RoutexSite("2", "B", "", 49.0, 3.0, null),
            RoutexSite("3", "C", "", 50.0, 4.0, null) // outside
        )
        // Bounds: lng 1..3, lat 47.5..49.5
        val result = RoutexClient.filterInBoundsAndLimit(sites, 1.0, 47.5, 3.0, 49.5, 10)
        assertEquals(2, result.size)
        assertEquals(listOf("1", "2"), result.map { it.id })
    }

    @Test
    fun filterInBoundsAndLimit_respectsMaxPois() {
        val sites = List(50) { i ->
            RoutexSite("id-$i", "S", "", 48.0 + i * 0.001, 2.0, null)
        }
        val result = RoutexClient.filterInBoundsAndLimit(sites, 1.0, 47.0, 3.0, 49.0, ROUTEX_MAX_POIS)
        assertEquals(ROUTEX_MAX_POIS, result.size)
    }

    @Test
    fun routexMaxPoisConstant_is20() {
        assertEquals(20, ROUTEX_MAX_POIS)
    }

    @Test
    fun radiusKmFromMapViewport_zoom12_approxParisView() {
        // Zoom 12, 400x400 px view at Paris: radius in reasonable range (tens of km)
        val r = radiusKmFromMapViewport(48.8566, 2.3522, 12f, 400, 400)
        assertTrue(r in 1..100, "radius $r km should be in reasonable range")
    }

    @Test
    fun radiusKmFromMapViewport_higherZoom_smallerRadius() {
        val r12 = radiusKmFromMapViewport(48.0, 2.0, 12f, 256, 256)
        val r14 = radiusKmFromMapViewport(48.0, 2.0, 14f, 256, 256)
        assertTrue(r14 < r12, "higher zoom (14) should give smaller radius than zoom 12")
    }

    /**
     * Bounds from curl to getResults (Lorraine area); user confirmed 8 stations for this request.
     * Format: minLng, minLat, maxLng, maxLat
     */
    private val CURL_BOUNDS_MIN_LNG = 6.658489491904622
    private val CURL_BOUNDS_MIN_LAT = 49.186270440448844
    private val CURL_BOUNDS_MAX_LNG = 7.273723866904622
    private val CURL_BOUNDS_MAX_LAT = 49.23763054241187

    @Test
    fun boundsRestrictScope_curlFixture_returns8StationsInBounds() {
        // Simulated API response: 8 stations inside curl bounds (x=lng, y=lat) + 2 outside to prove filtering
        val body = """
            [
                {"id":"1","x":6.75,"y":49.21,"brand_id":"A"},
                {"id":"2","x":6.82,"y":49.20,"brand_id":"B"},
                {"id":"3","x":6.90,"y":49.22,"brand_id":"C"},
                {"id":"4","x":6.95,"y":49.19,"brand_id":"D"},
                {"id":"5","x":7.05,"y":49.23,"brand_id":"E"},
                {"id":"6","x":7.12,"y":49.21,"brand_id":"F"},
                {"id":"7","x":7.18,"y":49.22,"brand_id":"G"},
                {"id":"8","x":7.22,"y":49.20,"brand_id":"H"},
                {"id":"out1","x":5.0,"y":49.0,"brand_id":"X"},
                {"id":"out2","x":8.0,"y":50.0,"brand_id":"Y"}
            ]
        """.trimIndent()
        val all = RoutexClient.parseResults(body)
        assertEquals(10, all.size, "parse should return all 10 items")

        val inBounds = RoutexClient.filterInBoundsAndLimit(
            all,
            CURL_BOUNDS_MIN_LNG,
            CURL_BOUNDS_MIN_LAT,
            CURL_BOUNDS_MAX_LNG,
            CURL_BOUNDS_MAX_LAT,
            maxPois = 20
        )
        assertEquals(8, inBounds.size, "bounds must restrict to 8 stations inside curl bounds")
        assertTrue(inBounds.all { it.id in listOf("1", "2", "3", "4", "5", "6", "7", "8") })
    }

    /**
     * Terville (Moselle, France) ~ 49.323°N, 6.134°E.
     * When using location (center + radius) the API returns this station; it must not be dropped
     * by overly tight bounds or by taking first 20 without sorting by distance.
     */
    private val TERVILLE_LAT = 49.323
    private val TERVILLE_LNG = 6.134

    @Test
    fun locationBasedRequest_tervilleStation_includedWhenInRadius() {
        // Location-based request: center such that Terville is within radius (e.g. Metz area, radius 15 km).
        // API returns stations by location/radius; Terville must be in the box and in results.
        val centerLat = 49.25
        val centerLng = 6.14
        val radiusKm = 15
        val body = """
            [
                {"id":"near1","x":6.14,"y":49.21,"brand_id":"A"},
                {"id":"near2","x":6.15,"y":49.20,"brand_id":"B"},
                {"id":"terville","x":${TERVILLE_LNG},"y":${TERVILLE_LAT},"brand_id":"Terville"},
                {"id":"near3","x":6.13,"y":49.22,"brand_id":"C"}
            ]
        """.trimIndent()
        val all = RoutexClient.parseResults(body)
        assertEquals(4, all.size)
        val box = boundsBoxFromCenterForTest(centerLat, centerLng, radiusKm)
        val filtered = RoutexClient.filterInBoundsAndLimit(
            all,
            box.minLng,
            box.minLat,
            box.maxLng,
            box.maxLat,
            maxPois = 20
        )
        assertTrue(
            filtered.any { it.id == "terville" },
            "Terville station (in radius) must be in results when using location-based box"
        )
        val tervilleSite = filtered.find { it.id == "terville" }
        assertTrue(kotlin.math.abs(tervilleSite!!.latitude - TERVILLE_LAT) < 0.001)
        assertTrue(kotlin.math.abs(tervilleSite.longitude - TERVILLE_LNG) < 0.001)
    }

    /** Same formula as RoutexClient.boundsBoxFromCenter for test use. */
    private fun boundsBoxFromCenterForTest(lat: Double, lng: Double, radiusKm: Int): BoundsBoxForTest {
        val latDelta = radiusKm / 111.0
        val lngDelta = radiusKm / (111.0 * kotlin.math.cos(lat * kotlin.math.PI / 180).coerceIn(0.01, 1.0))
        return BoundsBoxForTest(
            minLng = lng - lngDelta,
            minLat = lat - latDelta,
            maxLng = lng + lngDelta,
            maxLat = lat + latDelta
        )
    }

    private data class BoundsBoxForTest(
        val minLng: Double,
        val minLat: Double,
        val maxLng: Double,
        val maxLat: Double
    )
}
