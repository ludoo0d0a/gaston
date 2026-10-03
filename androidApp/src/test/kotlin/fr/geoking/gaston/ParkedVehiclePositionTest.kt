package fr.geoking.gaston

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParkedVehiclePositionTest {

    @Test
    fun upsertReplacesSameVehicle() {
        val first = ParkedVehiclePosition("v1", 48.0, 2.0, 1000L)
        val second = ParkedVehiclePosition("v1", 49.0, 3.0, 2000L)
        val settings = AppSettings().upsertParkedPosition(first).upsertParkedPosition(second)
        assertEquals(1, settings.parkedPositions.size)
        assertEquals(49.0, settings.parkedPositionFor("v1")!!.latitude, 0.0)
        assertEquals(2000L, settings.parkedPositionFor("v1")!!.savedAtEpochMs)
    }

    @Test
    fun clearRemovesOnlyTargetVehicle() {
        val settings = AppSettings()
            .upsertParkedPosition(ParkedVehiclePosition("v1", 48.0, 2.0, 1L))
            .upsertParkedPosition(ParkedVehiclePosition("v2", 45.0, 5.0, 2L))
            .clearParkedPosition("v1")
        assertNull(settings.parkedPositionFor("v1"))
        assertEquals(45.0, settings.parkedPositionFor("v2")!!.latitude, 0.0)
    }

    @Test
    fun latestParkedPositionPicksNewestTimestamp() {
        val settings = AppSettings()
            .upsertParkedPosition(ParkedVehiclePosition("v1", 48.0, 2.0, 100L))
            .upsertParkedPosition(ParkedVehiclePosition("v2", 45.0, 5.0, 300L))
            .upsertParkedPosition(ParkedVehiclePosition("v3", 46.0, 6.0, 200L))
        assertEquals("v2", settings.latestParkedPosition()!!.vehicleId)
        assertEquals(300L, settings.latestParkedPosition()!!.savedAtEpochMs)
    }
}
