package fr.geoking.gaston.aac

import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.road.RoadPolyline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RadarRoadCorridorCalculatorTest {

    @Test
    fun testNonRadarPoiReturnsNull() {
        val poi = Poi(
            id = "fuel-1",
            name = "Station",
            address = "1 Main St",
            poiCategory = PoiCategory.Gas,
            latitude = 48.8566,
            longitude = 2.3522,
            source = "test"
        )
        assertNull(RadarRoadCorridorCalculator.calculateCorridor(poi, emptyList()))
    }

    @Test
    fun testRadarPoiWithNoRoadsReturnsFallbackCorridor() {
        val poi = Poi(
            id = "radar-1",
            name = "Radar Fixe 80",
            address = "D900",
            poiCategory = PoiCategory.Radar,
            latitude = 48.8566,
            longitude = 2.3522,
            source = "test",
            rawSourceData = mapOf(
                "vma" to "80",
                DangerZoneTriangle.RAW_MONITORED_BEARING to "90"
            )
        )
        val corridor = RadarRoadCorridorCalculator.calculateCorridor(poi, emptyList())
        assertNotNull(corridor)
        assertEquals("radar-1", corridor.poiId)
        assertTrue(corridor.points.size >= 4)
        // Verify polygon loop is closed
        assertEquals(corridor.points.first(), corridor.points.last())
    }

    @Test
    fun testRadarPoiWithMatchingRoadPolylineGeneratesCorridor() {
        val poi = Poi(
            id = "radar-2",
            name = "Radar Autoroute 130",
            address = "A1",
            poiCategory = PoiCategory.Radar,
            latitude = 48.8500,
            longitude = 2.3500,
            source = "test",
            rawSourceData = mapOf(
                "vma" to "130",
                DangerZoneTriangle.RAW_MONITORED_BEARING to "0"
            )
        )

        // Road polyline running South-North through radar POI
        val road = RoadPolyline(
            id = 101L,
            highwayType = "motorway",
            name = "A1",
            points = listOf(
                48.8400 to 2.3500,
                48.8450 to 2.3500,
                48.8500 to 2.3500,
                48.8550 to 2.3500
            )
        )

        val corridor = RadarRoadCorridorCalculator.calculateCorridor(poi, listOf(road))
        assertNotNull(corridor)
        assertEquals("radar-2", corridor.poiId)
        assertTrue(corridor.points.size >= 4)
        assertEquals(corridor.points.first(), corridor.points.last())
    }
}
