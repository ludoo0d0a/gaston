package fr.geoking.gaston

import org.junit.Assert.assertEquals
import org.junit.Test

class MapFilterSettingsTest {

    @Test
    fun testCountryCodesAtMapPosition_MaizieresLesMetz() {
        // Maizières-lès-Metz is in France, about 40km away from Germany's border.
        // It must detect France (FR) and NOT Germany (DE).
        val codes = countryCodesAtMapPosition(49.212, 6.162)
        assertEquals(listOf("FR"), codes)
    }

    @Test
    fun testCountryCodesAtMapPosition_StrasbourgBorder() {
        // Strasbourg is in France but directly on the border with Germany (Kehl).
        // It must detect both France (FR) and Germany (DE) as it is well within 10km of the border.
        val codes = countryCodesAtMapPosition(48.583, 7.745)
        assertEquals(setOf("FR", "DE"), codes.toSet())
    }

    @Test
    fun testCountryCodesAtMapPosition_Frankfurt() {
        // Frankfurt is in Germany, far away from France.
        // It must detect Germany (DE) and NOT France (FR).
        val codes = countryCodesAtMapPosition(50.111, 8.682)
        assertEquals(listOf("DE"), codes)
    }

    @Test
    fun testCountryCodesAtMapPosition_Paris() {
        // Paris is in the center of France.
        // It must detect France (FR) and NOT Germany (DE).
        val codes = countryCodesAtMapPosition(48.8566, 2.3522)
        assertEquals(listOf("FR"), codes)
    }
}
