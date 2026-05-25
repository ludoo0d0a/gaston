package fr.geoking.gaston.api.us

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UsStateLookupTest {

    @Test
    fun nearestState_newYorkCity_resolvesNewYork() {
        val state = UsStateLookup.nearestState(40.7128, -74.0060)
        assertNotNull(state)
        assertEquals("NY", state.iso2)
        assertEquals("SNY", state.eiaDuoArea)
    }

    @Test
    fun nearestState_paris_isNull() {
        assertEquals(null, UsStateLookup.nearestState(48.8566, 2.3522))
    }

    @Test
    fun isInUnitedStates_continentalBounds() {
        assertTrue(UsStateLookup.isInUnitedStates(39.0, -98.0))
        assertFalse(UsStateLookup.isInUnitedStates(48.8, 2.3))
    }
}
