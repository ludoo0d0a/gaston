package fr.geoking.gaston.auto

import fr.geoking.gaston.poi.Poi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutoMapCameraTest {

    @Test
    fun selectMapFocusStations_returnsAtMostTwo_nearestFirst() {
        val stations = listOf(
            poiAt("far", 48.90, 2.50),
            poiAt("near", 48.860, 2.352),
            poiAt("mid", 48.870, 2.40),
        )
        val focus = AutoMapCamera.selectMapFocusStations(
            userLat = 48.85,
            userLon = 2.35,
            stations = stations,
        )
        assertEquals(2, focus.size)
        assertEquals("near", focus[0].id)
        assertEquals("mid", focus[1].id)
    }

    @Test
    fun selectMapFocusStations_emptyInput_returnsEmpty() {
        assertTrue(
            AutoMapCamera.selectMapFocusStations(48.85, 2.35, emptyList()).isEmpty()
        )
    }

    @Test
    fun cameraForMapFocus_noStations_usesDefaultZoom() {
        val camera = AutoMapCamera.cameraForMapFocus(
            userLat = 48.85,
            userLon = 2.35,
            stations = emptyList(),
            mapWidthPx = 800,
            mapHeightPx = 400,
        )
        assertEquals(48.85, camera.centerLat, 1e-6)
        assertEquals(2.35, camera.centerLon, 1e-6)
        assertEquals(AutoMapCamera.DEFAULT_ZOOM, camera.zoom)
    }

    @Test
    fun cameraForMapFocus_distantStation_lowersZoom() {
        val stations = listOf(poiAt("far", 48.90, 2.50))
        val camera = AutoMapCamera.cameraForMapFocus(
            userLat = 48.85,
            userLon = 2.35,
            stations = stations,
            mapWidthPx = 800,
            mapHeightPx = 400,
        )
        assertTrue(camera.zoom < AutoMapCamera.DEFAULT_ZOOM)
    }

    @Test
    fun cameraForMapFocus_manyStations_zoomHigherThanFitAll() {
        val userLat = 48.861
        val userLon = 2.353
        val stations = (0 until 20).map { i ->
            poiAt("s$i", userLat + 0.001 * (i % 5), userLon + 0.001 * (i / 5))
        }
        val focusCamera = AutoMapCamera.cameraForMapFocus(
            userLat = userLat,
            userLon = userLon,
            stations = stations,
            mapWidthPx = 800,
            mapHeightPx = 400,
        )
        val fitAllCamera = AutoMapCamera.fitToUserAndStations(
            userLat = userLat,
            userLon = userLon,
            stations = stations,
            mapWidthPx = 800,
            mapHeightPx = 400,
        )
        assertTrue(focusCamera.zoom > fitAllCamera.zoom)
    }

    @Test
    fun fitToUserAndStations_emptyStations_usesUserAndFallbackZoom() {
        val camera = AutoMapCamera.fitToUserAndStations(
            userLat = 48.85,
            userLon = 2.35,
            stations = emptyList(),
            mapWidthPx = 800,
            mapHeightPx = 400,
            fallbackZoom = 9,
        )
        assertEquals(48.85, camera.centerLat, 1e-6)
        assertEquals(2.35, camera.centerLon, 1e-6)
        assertEquals(9, camera.zoom)
    }

    @Test
    fun fitToUserAndStations_includesDistantStation_lowersZoom() {
        val stations = listOf(
            poiAt("a", 48.86, 2.36),
            poiAt("b", 48.90, 2.50),
        )
        val camera = AutoMapCamera.fitToUserAndStations(
            userLat = 48.85,
            userLon = 2.35,
            stations = stations,
            mapWidthPx = 800,
            mapHeightPx = 400,
        )
        assertTrue(camera.zoom < AutoMapCamera.DEFAULT_ZOOM)
        assertTrue(camera.centerLat in 48.84..48.91)
        assertTrue(camera.centerLon in 2.34..2.51)
    }

    @Test
    fun fitToUserAndStations_nearbyCluster_usesHigherZoom() {
        val stations = listOf(
            poiAt("a", 48.860, 2.352),
            poiAt("b", 48.862, 2.354),
        )
        val camera = AutoMapCamera.fitToUserAndStations(
            userLat = 48.861,
            userLon = 2.353,
            stations = stations,
            mapWidthPx = 800,
            mapHeightPx = 400,
        )
        assertTrue(camera.zoom >= 12)
    }

    @Test
    fun circleLatLngRing_closedAndApproxRadius() {
        val centerLat = 48.8566
        val centerLon = 2.3522
        val radiusKm = 10.0
        val ring = AutoMapCamera.circleLatLngRing(centerLat, centerLon, radiusKm, steps = 64)
        assertEquals(65, ring.size)
        assertEquals(ring.first().first, ring.last().first, 1e-9)
        assertEquals(ring.first().second, ring.last().second, 1e-9)
        val north = ring.maxOf { it.first }
        val approxKm = (north - centerLat) * 111.0
        assertEquals(radiusKm, approxKm, 0.05)
    }

    @Test
    fun radiusPxForKm_growsWhenZoomingIn() {
        val at14 = AutoMapCamera.radiusPxForKm(48.85, 14, 10.0)
        val at15 = AutoMapCamera.radiusPxForKm(48.85, 15, 10.0)
        assertTrue(at14 > 10f)
        assertTrue(at15 > at14)
    }

    private fun poiAt(id: String, lat: Double, lon: Double) = Poi(
        id = id,
        name = "Test",
        latitude = lat,
        longitude = lon,
        address = "",
        brand = null,
        isElectric = false,
    )
}
