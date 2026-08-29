package fr.geoking.gaston.parking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun containing_Warsaw_returnsPoland() {
        assertEquals(ParkingRegion.Poland, ParkingRegion.containing(52.2297, 21.0122))
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
    fun containing_Arlon_returnsBelgium_notLuxembourg() {
        // Arlon sits west of the LU bbox; must remain Belgium after the LU carve-out.
        assertEquals(ParkingRegion.Belgium, ParkingRegion.containing(49.6833, 5.8167))
    }

    @Test
    fun luxembourgAndBelgium_bboxesDoNotOverlap() {
        val lux = ParkingRegion.Luxembourg.subBoxes
        val be = ParkingRegion.Belgium.subBoxes
        for (a in lux) {
            for (b in be) {
                val overlap =
                    a.latMin <= b.latMax && a.latMax >= b.latMin &&
                        a.lonMin <= b.lonMax && a.lonMax >= b.lonMin
                kotlin.test.assertFalse(
                    overlap,
                    "LU $a overlaps BE $b",
                )
            }
        }
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

    @Test
    fun allContaining_Metz_returnsFrance() {
        // Metz: 49.11, 6.17
        val regions = ParkingRegion.allContaining(49.11, 6.17)
        assertEquals(1, regions.size)
        assertEquals(setOf(ParkingRegion.France), regions.toSet())
    }

    @Test
    fun allInViewport_nearBorder_returnsMultiple() {
        // Viewport covering part of FR, DE, LU (south of Liège — BE no longer overlaps LU)
        val regions = ParkingRegion.allInViewport(
            latMin = 49.4,
            latMax = 49.6,
            lonMin = 6.0,
            lonMax = 6.4
        )
        val codes = regions.map { it.countryCode }.toSet()
        assertEquals(setOf("FR", "DE", "LU"), codes)
    }
}
