package fr.geoking.gaston.aac

import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.shared.location.haversineKm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DangerZoneTriangleTest {

    @Test
    fun noBearingMeansFullCircleRing() {
        val poi = radarPoi(raw = mapOf("vma" to "130"))
        assertNull(DangerZoneTriangle.monitoredBearingDegrees(poi))
        val ring = assertNotNull(DangerZoneTriangle.latLngRingForRadarPoi(poi))
        assertTrue(ring.size >= 4)
        val overlays = DangerZoneTriangle.overlayRingsForRadarPoi(poi)
        assertTrue(overlays.isNotEmpty())
        assertEquals(DangerZoneOverlayKind.Full, overlays.first().kind)
        assertEquals(DangerZoneOverlayKind.Near, overlays.last().kind)
    }

    @Test
    fun bidirectionalMeansFullCircleRing() {
        val poi = radarPoi(
            raw = mapOf(
                "vma" to "90",
                DangerZoneTriangle.RAW_DIRECTION to "both",
                DangerZoneTriangle.RAW_BIDIRECTIONAL to "true",
            )
        )
        assertNull(DangerZoneTriangle.monitoredBearingDegrees(poi))
        assertNotNull(DangerZoneTriangle.latLngRingForRadarPoi(poi))
    }

    @Test
    fun alertRadiiForMotorway() {
        val radii = DangerZoneTriangle.alertRadiiMeters(4_000.0)
        assertEquals(listOf(4_000.0, 3_000.0, 2_000.0, 1_000.0, 200.0), radii)
    }

    @Test
    fun alertRadiiForUrbanHasNoKmReminders() {
        val radii = DangerZoneTriangle.alertRadiiMeters(300.0)
        assertEquals(listOf(300.0, 200.0), radii)
        assertTrue(DangerZoneTriangle.kmReminderRadiiMeters(300.0).isEmpty())
    }

    @Test
    fun sectorRingOpensOnApproachSide() {
        val tipLat = 48.8566
        val tipLon = 2.3522
        val bearing = 0.0 // northbound → approach from south
        val radiusM = 2_000.0
        val ring = DangerZoneTriangle.latLngRing(tipLat, tipLon, radiusM, bearing)
        assertTrue(ring.size >= 4)
        // Tip is first and last
        assertEquals(tipLat, ring.first().first, 1e-9)
        assertEquals(tipLon, ring.first().second, 1e-9)
        // Mid-arc point should be roughly south of tip at ~radius
        val mid = ring[ring.size / 2]
        assertTrue(mid.first < tipLat)
        val d = haversineKm(tipLat, tipLon, mid.first, mid.second) * 1000.0
        assertEquals(radiusM, d, 30.0)
    }

    @Test
    fun containsOnApproachAxisAndRejectsSideAndBehind() {
        val tipLat = 48.8566
        val tipLon = 2.3522
        val bearing = 0.0 // northbound → sector opens south
        val radiusM = 2_000.0

        val onAxis = DangerZoneTriangle.offsetMeters(tipLat, tipLon, 180.0, 1_000.0)
        assertTrue(
            DangerZoneTriangle.contains(
                onAxis.first, onAxis.second, tipLat, tipLon, radiusM, bearing,
            ),
        )

        // 90° east of tip at mid distance → outside 45° half-angle
        val farSide = DangerZoneTriangle.offsetMeters(tipLat, tipLon, 90.0, 1_000.0)
        assertFalse(
            DangerZoneTriangle.contains(
                farSide.first, farSide.second, tipLat, tipLon, radiusM, bearing,
            ),
        )

        // Behind the radar (north of tip) → outside
        val behind = DangerZoneTriangle.offsetMeters(tipLat, tipLon, 0.0, 200.0)
        assertFalse(
            DangerZoneTriangle.contains(
                behind.first, behind.second, tipLat, tipLon, radiusM, bearing,
            ),
        )

        // Within half-angle near entry (~30° from approach axis) → inside
        val angled = DangerZoneTriangle.offsetMeters(tipLat, tipLon, 180.0 - 30.0, 1_500.0)
        assertTrue(
            DangerZoneTriangle.contains(
                angled.first, angled.second, tipLat, tipLon, radiusM, bearing,
            ),
        )
    }

    @Test
    fun overlayRingsForMotorwayUnidirectional() {
        val poi = radarPoi(
            raw = mapOf(
                "vma" to "130",
                DangerZoneTriangle.RAW_MONITORED_BEARING to "0",
            )
        )
        val rings = DangerZoneTriangle.overlayRingsForRadarPoi(poi)
        assertEquals(5, rings.size)
        assertEquals(DangerZoneOverlayKind.Full, rings[0].kind)
        assertEquals(4_000.0, rings[0].radiusMeters)
        assertEquals(DangerZoneOverlayKind.KmReminder, rings[1].kind)
        assertEquals(DangerZoneOverlayKind.Near, rings.last().kind)
        assertEquals(200.0, rings.last().radiusMeters)
        // Largest first
        assertTrue(rings.zipWithNext().all { (a, b) -> a.radiusMeters > b.radiusMeters })
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
