package fr.geoking.gaston

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarMapModeTest {

    @Test
    fun removedAndRenamedModesAreMigrated() {
        assertEquals(CarMapMode.MapLibreMapSnapshotter, CarMapMode.fromStored("MapLibre"))
        assertEquals(CarMapMode.MapLibreVirtualDisplay, CarMapMode.fromStored("MapLibrePresentation"))
        assertEquals(CarMapMode.MapLibreEglDirect, CarMapMode.fromStored("MapLibreEgl"))
        assertEquals(CarMapMode.Mapsforge, CarMapMode.fromStored("Mapsforge"))
        assertEquals(CarMapMode.Native, CarMapMode.fromStored("garbage"))
        assertEquals(CarMapMode.Native, CarMapMode.fromStored(null))
    }

    @Test
    fun nextOnlyCyclesThroughSelectableModes() {
        var mode = CarMapMode.Native
        val seen = mutableSetOf<CarMapMode>()
        repeat(CarMapMode.entries.count { it.isSelectable }) {
            seen += mode
            assertTrue(mode.isSelectable)
            mode = mode.next()
        }
        assertEquals(CarMapMode.Native, mode)
        assertFalse(CarMapMode.MapLibreEglDirect in seen)
        assertFalse(CarMapMode.Mapbox in seen)
    }
}
