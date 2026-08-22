package fr.geoking.julius.ui.map

import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.ui.map.PhoneMapPoiHitTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneMapPoiHitTestTest {

    private fun poi(id: String, lat: Double = 48.0, lon: Double = 2.0) = Poi(
        id = id,
        name = "Station $id",
        address = "1 Rue Test",
        latitude = lat,
        longitude = lon,
    )

    @Test
    fun findNearestPoiAtScreenPoint_hitOnMarkerBody_returnsPoi() {
        val target = poi("a")
        val hit = PhoneMapPoiHitTest.findNearestPoiAtScreenPoint(
            screenX = 100f,
            screenY = 100f,
            pois = listOf(target),
            markerWidthPx = 120,
        ) { 100f to 120f }
        assertEquals(target, hit)
    }

    @Test
    fun findNearestPoiAtScreenPoint_farFromMarker_returnsNull() {
        val target = poi("a")
        val hit = PhoneMapPoiHitTest.findNearestPoiAtScreenPoint(
            screenX = 300f,
            screenY = 300f,
            pois = listOf(target),
            markerWidthPx = 120,
        ) { 100f to 120f }
        assertNull(hit)
    }

    @Test
    fun findNearestPoiAtScreenPoint_overlappingMarkers_returnsNearest() {
        val near = poi("near")
        val far = poi("far")
        val hit = PhoneMapPoiHitTest.findNearestPoiAtScreenPoint(
            screenX = 102f,
            screenY = 98f,
            pois = listOf(far, near),
            markerWidthPx = 120,
        ) { poi ->
            when (poi.id) {
                "near" -> 100f to 120f
                else -> 140f to 120f
            }
        }
        assertEquals(near, hit)
    }
}
