package fr.geoking.gaston.auto

import fr.geoking.gaston.poi.Poi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutoMapPoiHitTestTest {

    private val parisStation = Poi(
        id = "s1",
        name = "Station A",
        address = "Paris",
        latitude = 48.8566,
        longitude = 2.3522,
    )

    private val distantStation = Poi(
        id = "s2",
        name = "Station B",
        address = "Lyon",
        latitude = 45.7640,
        longitude = 4.8357,
    )

    @Test
    fun findPoisAt_hitOnMarkerCenter_returnsPoi() {
        val mapLat = 48.8566
        val mapLon = 2.3522
        val zoom = 13
        val centerPx = 400.0
        val (px, py) = AutoMapPoiHitTest.poiScreenPosition(
            parisStation,
            mapLat,
            mapLon,
            zoom,
            centerPx,
            centerPx,
        )

        val hits = AutoMapPoiHitTest.findPoisAt(
            screenX = px,
            screenY = py,
            pois = listOf(parisStation),
            mapLat = mapLat,
            mapLon = mapLon,
            zoom = zoom,
            mapBearingDegrees = 0f,
            centerPxX = centerPx,
            centerPxY = centerPx,
            visibleArea = null,
        )

        assertEquals(listOf(parisStation), hits)
    }

    @Test
    fun findPoisAt_missOutsideRadius_returnsEmpty() {
        val mapLat = 48.8566
        val mapLon = 2.3522
        val zoom = 13
        val centerPx = 400.0

        val hits = AutoMapPoiHitTest.findPoisAt(
            screenX = 0f,
            screenY = 0f,
            pois = listOf(parisStation),
            mapLat = mapLat,
            mapLon = mapLon,
            zoom = zoom,
            mapBearingDegrees = 0f,
            centerPxX = centerPx,
            centerPxY = centerPx,
            visibleArea = null,
        )

        assertTrue(hits.isEmpty())
    }

    @Test
    fun findPoisAt_returnsNearestFirst() {
        val mapLat = 48.8566
        val mapLon = 2.3522
        val zoom = 13
        val centerPx = 400.0
        val (px, py) = AutoMapPoiHitTest.poiScreenPosition(
            parisStation,
            mapLat,
            mapLon,
            zoom,
            centerPx,
            centerPx,
        )

        val hits = AutoMapPoiHitTest.findPoisAt(
            screenX = px,
            screenY = py,
            pois = listOf(distantStation, parisStation),
            mapLat = mapLat,
            mapLon = mapLon,
            zoom = zoom,
            mapBearingDegrees = 0f,
            centerPxX = centerPx,
            centerPxY = centerPx,
            visibleArea = null,
        )

        assertEquals(parisStation, hits.first())
    }

    @Test
    fun shouldZoomInsteadOfOpen_farApartStations_returnsFalse() {
        val mapLat = 48.8566
        val mapLon = 2.3522
        val zoom = 13
        val centerPx = 400.0

        assertFalse(
            AutoMapPoiHitTest.shouldZoomInsteadOfOpen(
                hits = listOf(parisStation, distantStation),
                mapLat = mapLat,
                mapLon = mapLon,
                zoom = zoom,
                centerPxX = centerPx,
                centerPxY = centerPx,
            ),
        )
    }
}
