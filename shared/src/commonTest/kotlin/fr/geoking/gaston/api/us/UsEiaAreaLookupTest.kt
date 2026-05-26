package fr.geoking.gaston.api.us

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UsEiaAreaLookupTest {

    @Test
    fun resolve_boston_returnsYbosMetro() {
        val area = UsEiaAreaLookup.resolve(42.3601, -71.0589)
        assertNotNull(area)
        assertEquals("YBOS", area.duoArea)
        assertEquals(UsEiaAreaLookup.Granularity.Metro, area.granularity)
        assertEquals("Boston metro", area.label)
    }

    @Test
    fun resolve_ruralMassachusetts_returnsSmaState() {
        // Western MA (Amherst) — outside Boston metro box
        val area = UsEiaAreaLookup.resolve(42.3732, -72.5199)
        assertNotNull(area)
        assertEquals("SMA", area.duoArea)
        assertEquals(UsEiaAreaLookup.Granularity.State, area.granularity)
        assertEquals("MA state", area.label)
    }

    @Test
    fun resolve_manhattan_returnsYnycMetro() {
        val area = UsEiaAreaLookup.resolve(40.7580, -73.9855)
        assertNotNull(area)
        assertEquals("YNYC", area.duoArea)
        assertEquals(UsEiaAreaLookup.Granularity.Metro, area.granularity)
    }

    @Test
    fun resolve_albany_returnsSnyState_notNycMetro() {
        val area = UsEiaAreaLookup.resolve(42.6526, -73.7562)
        assertNotNull(area)
        assertEquals("SNY", area.duoArea)
        assertEquals(UsEiaAreaLookup.Granularity.State, area.granularity)
        assertEquals("NY state", area.label)
    }

    @Test
    fun resolve_paris_isNull() {
        assertNull(UsEiaAreaLookup.resolve(48.8566, 2.3522))
    }

    @Test
    fun resolve_dc_usesMarylandDuoArea() {
        val area = UsEiaAreaLookup.resolve(38.9072, -77.0369)
        assertNotNull(area)
        assertEquals("SMD", area.duoArea)
        assertEquals("DC state", area.label)
    }
}
