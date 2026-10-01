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
    fun noTriangleWhenDirectionMissing() {
        val poi = radarPoi(raw = mapOf("vma" to "130"))
        assertNull(DangerZoneTriangle.monitoredBearingDegrees(poi))
        assertNull(DangerZoneTriangle.latLngRingForRadarPoi(poi))
    }

    @Test
    fun noTriangleWhenBidirectional() {
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
    fun triangleTipAtRadarAndBaseAtEntryRadius() {
        val tipLat = 48.8566
        val tipLon = 2.3522
        val bearing = 0.0 // northbound monitored → entry from south
        val radiusM = 2_000.0
        val ring = DangerZoneTriangle.latLngRing(tipLat, tipLon, radiusM, bearing)
        assertEquals(4, ring.size)
        assertEquals(tipLat, ring[0].first, 1e-9)
        assertEquals(tipLon, ring[0].second, 1e-9)
        assertEquals(tipLat, ring[3].first, 1e-9)

        val dLeft = haversineKm(tipLat, tipLon, ring[1].first, ring[1].second) * 1000.0
        val dRight = haversineKm(tipLat, tipLon, ring[2].first, ring[2].second) * 1000.0
        assertEquals(radiusM, dLeft, 5.0)
        assertEquals(radiusM, dRight, 5.0)
        // Base mid-point is south of tip (approach from 180°)
        val midLat = (ring[1].first + ring[2].first) / 2.0
        assertTrue(midLat < tipLat)
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
