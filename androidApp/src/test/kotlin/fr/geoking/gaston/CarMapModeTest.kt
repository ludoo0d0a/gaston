package fr.geoking.gaston

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarMapModeTest {

    @Test
    fun removedAndRenamedModesAreMigrated() {
        // Feature-flagged-off modes coerce to the production default (Mapsforge).
        assertEquals(CarMapMode.DEFAULT_PRODUCTION, CarMapMode.fromStored("MapLibre"))
        assertEquals(CarMapMode.DEFAULT_PRODUCTION, CarMapMode.fromStored("MapLibrePresentation"))
        assertEquals(CarMapMode.DEFAULT_PRODUCTION, CarMapMode.fromStored("MapLibreEgl"))
        assertEquals(CarMapMode.Mapsforge, CarMapMode.fromStored("Mapsforge"))
        assertEquals(CarMapMode.DEFAULT_PRODUCTION, CarMapMode.fromStored("garbage"))
        assertEquals(CarMapMode.DEFAULT_PRODUCTION, CarMapMode.fromStored(null))
    }

    @Test
    fun nextOnlyCyclesThroughSelectableModes() {
        val start = CarMapMode.entries.first { it.isSelectable }
        var mode = start
        val seen = mutableSetOf<CarMapMode>()
        repeat(CarMapMode.entries.count { it.isSelectable }) {
            seen += mode
            assertTrue(mode.isSelectable)
            mode = mode.next()
        }
        assertEquals(start, mode)
        assertFalse(CarMapMode.MapLibreEglDirect in seen)
        assertFalse(CarMapMode.Mapbox in seen)
        assertTrue(CarMapMode.Mapsforge in seen)
    }
}
