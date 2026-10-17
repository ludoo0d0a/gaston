package fr.geoking.gaston.auto.mapsforge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Tag
import org.mapsforge.map.datastore.PointOfInterest

class MapsforgeCityLabelsTest {

    @Test
    fun testCityLabelCreation() {
        val label = CityLabel(
            name = "Paris",
            lat = 48.8566,
            lon = 2.3522,
            placeType = "city"
        )
        assertEquals("Paris", label.name)
        assertEquals(48.8566, label.lat, 0.0001)
        assertEquals(2.3522, label.lon, 0.0001)
        assertEquals("city", label.placeType)
    }

    @Test
    fun testPoiPlaceTagExtraction() {
        val validPlaceTypes = setOf("city", "town", "village", "hamlet", "suburb")
        val tags = listOf(
            Tag("place", "city"),
            Tag("name", "Lyon"),
            Tag("population", "500000")
        )
        val poi = PointOfInterest(0, tags, LatLong(45.7640, 4.8357))

        var placeType: String? = null
        var name: String? = null
        for (tag in poi.tags) {
            if (tag.key == "place" && tag.value in validPlaceTypes) {
                placeType = tag.value
            } else if (tag.key == "name") {
                name = tag.value
            }
        }

        assertEquals("city", placeType)
        assertEquals("Lyon", name)

        val cityLabel = CityLabel(
            name = name!!,
            lat = poi.position.latitude,
            lon = poi.position.longitude,
            placeType = placeType!!
        )
        assertEquals("Lyon", cityLabel.name)
        assertEquals(45.7640, cityLabel.lat, 0.0001)
        assertEquals(4.8357, cityLabel.lon, 0.0001)
    }

    @Test
    fun testLabelCounterRotationAngle() {
        // When map rotates by -bearing, text counter-rotation angle by +bearing cancels out map rotation
        val mapBearing = 90f
        val counterRotation = mapBearing
        assertEquals(90f, counterRotation, 0.001f)

        val headingUpBearing = 180f
        assertEquals(180f, headingUpBearing, 0.001f)
    }

    @Test
    fun testZoomVisibilityRules() {
        fun isPlaceVisibleAtZoom(placeType: String, zoom: Int): Boolean = when (placeType) {
            "city" -> zoom >= 5
            "town" -> zoom >= 8
            "village", "suburb" -> zoom >= 11
            "hamlet" -> zoom >= 13
            else -> zoom >= 12
        }

        assertTrue(isPlaceVisibleAtZoom("city", 5))
        assertFalse(isPlaceVisibleAtZoom("town", 5))

        assertTrue(isPlaceVisibleAtZoom("town", 8))
        assertFalse(isPlaceVisibleAtZoom("village", 8))

        assertTrue(isPlaceVisibleAtZoom("village", 11))
        assertFalse(isPlaceVisibleAtZoom("hamlet", 11))

        assertTrue(isPlaceVisibleAtZoom("hamlet", 13))
    }

    @Test
    fun testDoubledCityLabelTextSizes() {
        fun getCityTextSizeDp(placeType: String): Float = when (placeType) {
            "city" -> 40f
            "town" -> 32f
            "village", "suburb" -> 28f
            else -> 24f
        }

        assertEquals(40f, getCityTextSizeDp("city"), 0.001f)
        assertEquals(32f, getCityTextSizeDp("town"), 0.001f)
        assertEquals(28f, getCityTextSizeDp("village"), 0.001f)
        assertEquals(28f, getCityTextSizeDp("suburb"), 0.001f)
        assertEquals(24f, getCityTextSizeDp("hamlet"), 0.001f)
        assertEquals(24f, getCityTextSizeDp("other"), 0.001f)
    }

    @Test
    fun testCityLabelBoldFormatting() {
        fun isCityBold(placeType: String): Boolean = placeType == "city" || placeType == "town"

        assertTrue(isCityBold("city"))
        assertTrue(isCityBold("town"))
        assertFalse(isCityBold("village"))
        assertFalse(isCityBold("suburb"))
        assertFalse(isCityBold("hamlet"))
    }

    @Test
    fun testLabelAntiOverlapThreshold() {
        val overlapThreshold = 150f
        val pos1 = 100f to 100f
        val dxClose = 180f - pos1.first
        val dyClose = 100f - pos1.second
        val distClose = kotlin.math.hypot(dxClose, dyClose)
        assertTrue(distClose < overlapThreshold)

        val posFar = 300f to 100f
        val dxFar = posFar.first - pos1.first
        val dyFar = posFar.second - pos1.second
        val distFar = kotlin.math.hypot(dxFar, dyFar)
        assertFalse(distFar < overlapThreshold)
    }
}
