package fr.geoking.gaston.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

class AvailabilityBarLayoutTest {

    @Test
    fun `barStates returns one bar per connector when total is within cap`() {
        assertEquals(listOf(true, true, false), AvailabilityBarLayout.barStates(2, 3))
    }

    @Test
    fun `barStates scales down when total exceeds cap`() {
        assertEquals(listOf(true, true, false, false, false), AvailabilityBarLayout.barStates(4, 10))
    }

    @Test
    fun `barStates returns empty when total is zero`() {
        assertEquals(emptyList<Boolean>(), AvailabilityBarLayout.barStates(0, 0))
    }

    @Test
    fun `availabilityColor returns green when availability exceeds 25 percent`() {
        // 3 out of 10 = 30% > 25% -> Green
        assertEquals(AvailabilityBarLayout.greenColor, AvailabilityBarLayout.availabilityColor(3, 10))
    }

    @Test
    fun `availabilityColor returns orange when availability is 25 percent or less`() {
        // 2 out of 8 = 25% <= 25% -> Orange
        assertEquals(AvailabilityBarLayout.orangeColor, AvailabilityBarLayout.availabilityColor(2, 8))
        // 1 out of 10 = 10% <= 25% -> Orange
        assertEquals(AvailabilityBarLayout.orangeColor, AvailabilityBarLayout.availabilityColor(1, 10))
    }

    @Test
    fun `availabilityColor returns red when complete or zero available`() {
        assertEquals(AvailabilityBarLayout.redColor, AvailabilityBarLayout.availabilityColor(0, 10))
        assertEquals(AvailabilityBarLayout.redColor, AvailabilityBarLayout.availabilityColor(0, 0))
    }

    @Test
    fun `barColor returns statusColor when available and occupiedColor when unavailable`() {
        val statusColor = AvailabilityBarLayout.orangeColor
        assertEquals(statusColor, AvailabilityBarLayout.barColor(true, statusColor))
        assertEquals(AvailabilityBarLayout.occupiedColor, AvailabilityBarLayout.barColor(false, statusColor))
    }
}
