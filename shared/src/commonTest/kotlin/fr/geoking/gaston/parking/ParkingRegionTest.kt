package fr.geoking.gaston.parking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ParkingRegionTest {

    @Test
    fun containing_Paris_returnsFrance() {
        assertEquals(ParkingRegion.France, ParkingRegion.containing(48.8566, 2.3522))
    }

    @Test
    fun containing_Berlin_returnsGermany() {
        assertEquals(ParkingRegion.Germany, ParkingRegion.containing(52.52, 13.405))
    }

    @Test
    fun containing_Zurich_returnsSwitzerland() {
        assertEquals(ParkingRegion.Switzerland, ParkingRegion.containing(47.3769, 8.5417))
    }

    @Test
    fun containing_LuxembourgCity_returnsLuxembourg() {
        assertEquals(ParkingRegion.Luxembourg, ParkingRegion.containing(49.6116, 6.1319))
    }

    @Test
    fun containing_Brussels_returnsBelgium() {
        assertEquals(ParkingRegion.Belgium, ParkingRegion.containing(50.8503, 4.3517))
    }

    @Test
    fun containing_Aarhus_returnsDenmark() {
        assertEquals(ParkingRegion.Denmark, ParkingRegion.containing(56.1629, 10.2039))
    }

    @Test
    fun containing_outsideEurope_returnsNull() {
        assertNull(ParkingRegion.containing(40.7128, -74.0060))
    }
}
