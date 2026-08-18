package fr.geoking.gaston.poi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StationNameTest {

    @Test
    fun genericStationName_frAndEn() {
        assertEquals("Station", genericStationName(lang = "fr"))
        assertEquals("Gas station", genericStationName(lang = "en"))
        assertEquals("Station Paris", genericStationName("Paris", lang = "fr"))
        assertEquals("Gas station Lyon", genericStationName("Lyon", lang = "en"))
        assertEquals("Station", genericStationName("  ", lang = "fr"))
        assertEquals("Station SEMéCOURT", genericStationName("SEMéCOURT", lang = "fr"))
    }

    @Test
    fun isGenericStationName_exactLabels() {
        assertTrue(isGenericStationName(null))
        assertTrue(isGenericStationName(""))
        assertTrue(isGenericStationName("  "))
        assertTrue(isGenericStationName("Station"))
        assertTrue(isGenericStationName("station"))
        assertTrue(isGenericStationName("GAS STATION"))
        assertTrue(isGenericStationName("Gas station"))
        assertTrue(isGenericStationName("Station-service"))
        assertTrue(isGenericStationName("station service"))
    }

    @Test
    fun isGenericStationName_withCity() {
        assertTrue(isGenericStationName("Station Paris"))
        assertTrue(isGenericStationName("Station SEMéCOURT"))
        assertTrue(isGenericStationName("Gas station Lyon"))
        assertTrue(isGenericStationName("Station-service Marseille"))
        assertEquals("SEMéCOURT", genericStationCity("Station SEMéCOURT"))
        assertEquals("Lyon", genericStationCity("Gas station Lyon"))
        assertNull(genericStationCity("Station"))
        assertNull(genericStationCity("Esso Roquette"))
    }

    @Test
    fun isGenericStationName_rejectsBrandedAndSpecific() {
        assertFalse(isGenericStationName("Station U"))
        assertFalse(isGenericStationName("Station Total"))
        assertFalse(isGenericStationName("Esso Roquette"))
        assertFalse(isGenericStationName("Gazole Station"))
        assertFalse(isGenericStationName("Total Access République"))
    }
}
