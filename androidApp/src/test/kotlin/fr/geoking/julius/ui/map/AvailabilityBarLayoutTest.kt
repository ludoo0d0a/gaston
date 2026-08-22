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
}
