package fr.geoking.gaston.poi

import fr.geoking.gaston.api.datagouv.DataGouvProvider
import fr.geoking.gaston.api.datagouv.DataGouvPrixCarburantProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapViewportBoundaryTest {

    @Test
    fun radiusKmFromMapViewport_calculatesCorrectRadiusFromZoomAndDimensions() {
        // Near Metz / Thionville: lat = 49.19315887687151, lng = 6.145889998649892
        // At zoom 12.0 with viewport 1080x2340 px
        val radius = radiusKmFromMapViewport(
            centerLat = 49.19315887687151,
            centerLng = 6.145889998649892,
            zoom = 12.0f,
            mapWidthPx = 1080,
            mapHeightPx = 2340
        )
        // 32.13 km -> ceil -> 33 km
        assertEquals(33, radius)
    }

    @Test
    fun radiusKmFromMapViewport_calculatesCorrectRadiusFromExplicitBounds() {
        val centerLat = 49.19315887687151
        val centerLng = 6.145889998649892
        val viewport = calculateBoundsFromMapViewport(
            centerLat = centerLat,
            centerLng = centerLng,
            zoom = 12.0f,
            mapWidthPx = 1080,
            mapHeightPx = 2340
        )

        val radius = radiusKmFromMapViewport(
            centerLat = centerLat,
            centerLng = centerLng,
            viewport = viewport
        )

        // The circumscribed circle covering the map boundary corners should match 33 km
        assertEquals(33, radius)
    }

    @Test
    fun dataGouvProvider_queriesCircumscribedCircleForMapViewport() = runBlocking {
        var requestedUrl = ""
        val mockEngine = MockEngine { request ->
            requestedUrl = request.url.toString()
            respond(
                content = """{"results": []}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val httpClient = HttpClient(mockEngine)
        val provider = DataGouvProvider(httpClient)

        val centerLat = 49.19315887687151
        val centerLng = 6.145889998649892
        val viewport = calculateBoundsFromMapViewport(
            centerLat = centerLat,
            centerLng = centerLng,
            zoom = 12.0f,
            mapWidthPx = 1080,
            mapHeightPx = 2340
        )

        provider.getGasStations(centerLat, centerLng, viewport)

        assertTrue(requestedUrl.contains("within_distance"))
        assertTrue(requestedUrl.contains("33km"))
    }

    @Test
    fun dataGouvPrixCarburantProvider_queriesCircumscribedCircleForMapViewport() = runBlocking {
        var requestedUrl = ""
        val mockEngine = MockEngine { request ->
            requestedUrl = request.url.toString()
            respond(
                content = """{"results": []}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val httpClient = HttpClient(mockEngine)
        val provider = DataGouvPrixCarburantProvider(httpClient)

        val centerLat = 49.19315887687151
        val centerLng = 6.145889998649892
        val viewport = calculateBoundsFromMapViewport(
            centerLat = centerLat,
            centerLng = centerLng,
            zoom = 12.0f,
            mapWidthPx = 1080,
            mapHeightPx = 2340
        )

        provider.getGasStations(centerLat, centerLng, viewport)

        assertTrue(requestedUrl.contains("within_distance"))
        assertTrue(requestedUrl.contains("33km"))
    }
}
