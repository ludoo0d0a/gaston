package fr.geoking.gaston.transit

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransitRegionTest {

    @Test
    fun france_contains_paris() {
        assertTrue(TransitRegion.France.contains(48.8566, 2.3522))
    }

    @Test
    fun france_contains_lyon() {
        assertTrue(TransitRegion.France.contains(45.7640, 4.8357))
    }

    @Test
    fun france_doesNotContain_london() {
        assertFalse(TransitRegion.France.contains(51.5074, -0.1278))
    }

    @Test
    fun belgium_contains_brussels() {
        assertTrue(TransitRegion.Belgium.contains(50.8503, 4.3517))
    }

    @Test
    fun belgium_doesNotContain_paris() {
        assertFalse(TransitRegion.Belgium.contains(48.8566, 2.3522))
    }

    @Test
    fun luxembourg_contains_luxembourg_city() {
        assertTrue(TransitRegion.Luxembourg.contains(49.6116, 6.1319))
    }

    @Test
    fun luxembourg_doesNotContain_brussels() {
        assertFalse(TransitRegion.Luxembourg.contains(50.8503, 4.3517))
    }

    @Test
    fun containing_paris_returnsFrance() {
        assertTrue(TransitRegion.containing(48.8566, 2.3522) == TransitRegion.France)
    }

    @Test
    fun containing_brussels_returnsBelgium() {
        assertTrue(TransitRegion.containing(50.8503, 4.3517) == TransitRegion.Belgium)
    }

    @Test
    fun containing_luxembourgCity_returnsLuxembourg() {
        assertTrue(TransitRegion.containing(49.6116, 6.1319) == TransitRegion.Luxembourg)
    }

    @Test
    fun containing_london_returnsNull() {
        assertNull(TransitRegion.containing(51.5074, -0.1278))
    }
}
