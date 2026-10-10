package fr.geoking.gaston.shared.logging

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RadarDetectionLogTest {

    @BeforeTest
    fun setUp() {
        DebugLogStore.clearAll()
    }

    @Test
    fun logWritesFilterableRadarHostEntryWhenEnabled() {
        RadarDetectionLog.log(
            event = RadarDetectionLog.Event.ENTRY,
            zoneId = "z-urban-1",
            kind = "SpeedControlArea",
            roadClass = "Urban",
            radiusMeters = 300.0,
            distanceMeters = 180.0,
            vmaKmH = 50,
            speedKmH = 48.0,
            isAhead = true,
            isOverspeed = false,
            source = "france-radars",
            enabled = true,
        )

        val logs = DebugLogStore.logs.value
        assertEquals(1, logs.size)
        val entry = logs.first()
        assertEquals(RadarDetectionLog.HOST, entry.host)
        assertEquals("ENTRY", entry.method)
        assertEquals(200, entry.statusCode)
        assertTrue(entry.url.contains("radar://entry/z-urban-1"))
        assertTrue(entry.url.contains("vma=50"))
        assertTrue(entry.url.contains("dist=180m"))
        assertTrue(entry.url.contains("radius=300m"))
        assertTrue(entry.url.contains("road=Urban"))
        assertTrue(entry.responseBody.orEmpty().contains("vma=50"))
        assertEquals("Radar detection", resolveProviderName(entry.host))
    }

    @Test
    fun logIsNoOpWhenDisabled() {
        RadarDetectionLog.log(
            event = RadarDetectionLog.Event.NEAR,
            zoneId = "z1",
            kind = "SpeedControlArea",
            roadClass = "Motorway",
            radiusMeters = 4000.0,
            distanceMeters = 80.0,
            vmaKmH = 130,
            speedKmH = 120.0,
            isAhead = true,
            isOverspeed = false,
            enabled = false,
        )
        assertTrue(DebugLogStore.logs.value.isEmpty())
    }

    @Test
    fun overspeedUsesNonSuccessStatusForVisibility() {
        RadarDetectionLog.log(
            event = RadarDetectionLog.Event.ENTRY,
            zoneId = "z-fast",
            kind = "SpeedControlArea",
            roadClass = "ExtraUrban",
            radiusMeters = 2000.0,
            distanceMeters = 500.0,
            vmaKmH = 90,
            speedKmH = 110.0,
            isAhead = true,
            isOverspeed = true,
            enabled = true,
        )
        assertEquals(400, DebugLogStore.logs.value.first().statusCode)
    }
}
