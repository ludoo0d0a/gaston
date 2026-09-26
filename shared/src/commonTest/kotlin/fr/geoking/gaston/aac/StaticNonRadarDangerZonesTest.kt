package fr.geoking.gaston.aac

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StaticNonRadarDangerZonesTest {
    @Test
    fun sampleZonesAreAccidentProneNotRadarDerived() {
        val zones = StaticNonRadarDangerZones.all()
        assertTrue(zones.isNotEmpty())
        assertTrue(zones.all { it.kind == DangerZoneKind.AccidentProne })
        assertTrue(zones.all { it.source == StaticNonRadarDangerZones.SOURCE })
        assertFalse(zones.any { it.source == "FranceRadars" })
    }

    @Test
    fun nearParisIncludesPeriphSample() {
        val near = StaticNonRadarDangerZones.near(48.82, 2.36, radiusKm = 30.0)
        assertTrue(near.any { it.id.contains("periph") })
    }
}
