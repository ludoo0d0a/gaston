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

    @Test
    fun corridorRingsFallsBackToSyntheticWhenNoRoads() {
        val rings = RadarRoadCorridorCalculator.corridorRings(
            tipLat = 48.8566,
            tipLon = 2.3522,
            radiusMeters = 2_000.0,
            monitoredBearingDegrees = 90.0,
            bidirectional = false,
            roads = emptyList(),
        )
        assertEquals(1, rings.size)
        assertTrue(rings[0].size >= 4)
        assertEquals(rings[0].first(), rings[0].last())
    }

    @Test
    fun corridorRingsSyntheticIsBidirectionalWhenAsked() {
        val rings = RadarRoadCorridorCalculator.syntheticCorridorRings(
            tipLat = 48.8566,
            tipLon = 2.3522,
            radiusMeters = 1_000.0,
            monitoredBearingDegrees = 0.0,
            bidirectional = true,
        )
        assertEquals(2, rings.size)
    }

    @Test
    fun corridorFollowsRoadOnApproachSideForFullRadiusAcrossWays() {
        // Road runs north (bearing 0). Controlled traffic heads north → approach side is south.
        // Two chained ways: radar sits on the first, the second continues 1.5 km further south.
        val radarLat = 48.8500
        val wayNorth = RoadPolyline(1L, "primary", "D1", listOf(48.8400 to 2.35, 48.8600 to 2.35))
        val southEnd = wayNorth.points.first()
        val waySouth = RoadPolyline(2L, "primary", "D1", listOf(48.8300 to 2.35, southEnd.first to southEnd.second))
        val rings = RadarRoadCorridorCalculator.corridorRings(
            tipLat = radarLat, tipLon = 2.35, radiusMeters = 2_000.0,
            monitoredBearingDegrees = 0.0, bidirectional = false,
            roads = listOf(wayNorth, waySouth),
        )
        assertEquals(1, rings.size)
        val zone = DangerZone(
            id = "z", centerLatitude = radarLat, centerLongitude = 2.35, radiusMeters = 2_000.0,
            speedLimitKmH = 80, kind = DangerZoneKind.SpeedControlArea, source = "t",
            roadClass = RoadNetworkClass.ExtraUrban, monitoredBearingDegrees = 0.0,
        ).withRoadCorridor(rings)
        // ~1.8 km south along the road (second way): inside; north of the radar: outside.
        assertTrue(zone.contains(radarLat - 0.0162, 2.35))
        assertTrue(!zone.contains(radarLat + 0.002, 2.35))
        // Beyond 2 km along the road: outside.
        assertTrue(!zone.contains(radarLat - 0.0200, 2.35))
        // Off the road by ~200 m: outside (sector would have accepted it).
        assertTrue(!zone.contains(radarLat - 0.005, 2.3528))
    }
}
