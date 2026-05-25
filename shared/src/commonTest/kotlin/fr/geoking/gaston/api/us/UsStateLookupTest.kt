package fr.geoking.gaston.api.us

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UsStateLookupTest {

    @Test
    fun nearestState_albany_resolvesNewYork() {
        // Albany, NY is closer to NY centroid than NJ centroid.
        // NYC is actually closer to NJ centroid than NY centroid.
        val state = UsStateLookup.nearestState(42.6526, -73.7562)
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

    @Test
    fun isInUnitedStates_expandedBounds() {
        // Puerto Rico
        assertTrue(UsStateLookup.isInUnitedStates(18.2, -66.5))
        // US Virgin Islands
        assertTrue(UsStateLookup.isInUnitedStates(18.3, -64.8))
        // Alaska
        assertTrue(UsStateLookup.isInUnitedStates(64.2, -149.5))
        // Hawaii
        assertTrue(UsStateLookup.isInUnitedStates(21.3, -157.8))
    }
}
