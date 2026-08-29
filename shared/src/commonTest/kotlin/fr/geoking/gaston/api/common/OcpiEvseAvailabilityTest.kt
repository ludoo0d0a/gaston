package fr.geoking.gaston.api.common

import fr.geoking.gaston.api.belib.AvailabilityStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OcpiEvseAvailabilityTest {

    @Test
    fun counts_skipsRemoved_andCountsAvailable() {
        val (available, total) = OcpiEvseAvailability.counts(
            listOf("AVAILABLE", "CHARGING", "REMOVED", "BLOCKED", "FREE")
        )
        assertEquals(2, available)
        assertEquals(4, total)
    }

    @Test
    fun mapStatus_ocpiValues() {
        assertEquals(AvailabilityStatus.Available, OcpiEvseAvailability.mapStatus("AVAILABLE"))
        assertEquals(AvailabilityStatus.Occupied, OcpiEvseAvailability.mapStatus("CHARGING"))
        assertEquals(AvailabilityStatus.Maintenance, OcpiEvseAvailability.mapStatus("OUTOFORDER"))
        assertEquals(AvailabilityStatus.Removed, OcpiEvseAvailability.mapStatus("REMOVED"))
    }

    @Test
    fun isAvailable_acceptsFreeAndIdle() {
        assertTrue(OcpiEvseAvailability.isAvailable("FREE"))
        assertTrue(OcpiEvseAvailability.isAvailable("idle"))
        assertFalse(OcpiEvseAvailability.isAvailable("CHARGING"))
    }
}
