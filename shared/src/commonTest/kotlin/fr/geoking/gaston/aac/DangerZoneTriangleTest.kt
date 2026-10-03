package fr.geoking.gaston.aac

import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.shared.location.haversineKm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DangerZoneTriangleTest {

    @Test
    fun noRectangleWhenDirectionMissing() {
        val poi = radarPoi(raw = mapOf("vma" to "130"))
        assertNull(DangerZoneTriangle.monitoredBearingDegrees(poi))
        assertNull(DangerZoneTriangle.latLngRingForRadarPoi(poi))
    }

    @Test
    fun noRectangleWhenBidirectional() {
        val poi = radarPoi(
            raw = mapOf(
                "vma" to "90",
                DangerZoneTriangle.RAW_DIRECTION to "both",
                DangerZoneTriangle.RAW_BIDIRECTIONAL to "true",
            )
        )
        assertNull(DangerZoneTriangle.monitoredBearingDegrees(poi))
        assertNull(DangerZoneTriangle.latLngRingForRadarPoi(poi))
    }

    @Test
    fun rectangleNearEdgeAtRadarAndFarEdgeAtEntryRadius() {
        val tipLat = 48.8566
        val tipLon = 2.3522
        val bearing = 0.0 // northbound monitored → entry from south
        val radiusM = 2_000.0
        val halfW = DangerZoneTriangle.HALF_WIDTH_METERS
        val ring = DangerZoneTriangle.latLngRing(tipLat, tipLon, radiusM, bearing)
        assertEquals(5, ring.size)
        // Closed ring
        assertEquals(ring[0].first, ring[4].first, 1e-9)
        assertEquals(ring[0].second, ring[4].second, 1e-9)

        // Near corners are ~halfWidth from tip
        val dNearLeft = haversineKm(tipLat, tipLon, ring[0].first, ring[0].second) * 1000.0
        val dNearRight = haversineKm(tipLat, tipLon, ring[3].first, ring[3].second) * 1000.0
        assertEquals(halfW, dNearLeft, 5.0)
        assertEquals(halfW, dNearRight, 5.0)

        // Far-edge mid-point is south of tip at ~radius
        val midFarLat = (ring[1].first + ring[2].first) / 2.0
        val midFarLon = (ring[1].second + ring[2].second) / 2.0
        assertTrue(midFarLat < tipLat)
        val dFar = haversineKm(tipLat, tipLon, midFarLat, midFarLon) * 1000.0
        assertEquals(radiusM, dFar, 10.0)

        // Length along corridor ≈ radius; width ≈ 2 * halfWidth
        val widthNear = haversineKm(ring[0].first, ring[0].second, ring[3].first, ring[3].second) * 1000.0
        assertEquals(2.0 * halfW, widthNear, 8.0)
    }

    @Test
    fun usesStoredMonitoredBearing() {
        val poi = radarPoi(
            raw = mapOf(
                "vma" to "50",
                DangerZoneTriangle.RAW_MONITORED_BEARING to "90",
            )
        )
        assertEquals(90.0, DangerZoneTriangle.monitoredBearingDegrees(poi)!!)
        assertNotNull(DangerZoneTriangle.latLngRingForRadarPoi(poi))
    }

    @Test
    fun resolvesNumericDirectionTag() {
        val poi = radarPoi(
            raw = mapOf(
                "vma" to "110",
                DangerZoneTriangle.RAW_DIRECTION to "270",
            )
        )
        assertEquals(270.0, DangerZoneTriangle.monitoredBearingDegrees(poi)!!)
    }

    private fun radarPoi(raw: Map<String, String>): Poi = Poi(
        id = "t1",
        name = "Zone",
        address = "",
        latitude = 48.85,
        longitude = 2.35,
        poiCategory = PoiCategory.Radar,
        source = "test",
        rawSourceData = raw,
    )
}
