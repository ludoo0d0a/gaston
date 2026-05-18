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
    fun searchZoomLevels_includesInitialAndThreeWiderSteps() {
        val levels = AutoMapCamera.searchZoomLevels(13)
        assertEquals(listOf(13, 11, 9, 7), levels)
    }

    @Test
    fun searchZoomLevels_stopsAtMinZoom() {
        val levels = AutoMapCamera.searchZoomLevels(5)
        assertEquals(listOf(5, 4), levels)
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
            poiAt(48.86, 2.36),
            poiAt(48.90, 2.50),
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
            poiAt(48.860, 2.352),
            poiAt(48.862, 2.354),
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

    private fun poiAt(lat: Double, lon: Double) = Poi(
        id = "$lat,$lon",
        name = "Test",
        latitude = lat,
        longitude = lon,
        address = "",
        brand = null,
        isElectric = false,
    )
}
