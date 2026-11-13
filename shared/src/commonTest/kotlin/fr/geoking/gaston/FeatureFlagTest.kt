package fr.geoking.gaston

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeatureFlagTest {

    @AfterTest
    fun tearDown() {
        FeatureFlag.clearOverrides()
    }

    @Test
    fun productionCountriesAreFranceAndLuxembourgOnly() {
        assertTrue(FeatureFlag.isCountryEnabled("FR"))
        assertTrue(FeatureFlag.isCountryEnabled("LU"))
        assertFalse(FeatureFlag.isCountryEnabled("DE"))
        assertFalse(FeatureFlag.isCountryEnabled("BE"))
        assertFalse(FeatureFlag.isCountryEnabled("US"))
        assertEquals(setOf("FR", "LU"), FeatureFlag.enabledCountryCodes())
    }

    @Test
    fun complexLayersAreOff() {
        assertFalse(FeatureFlag.RADAR)
        assertFalse(FeatureFlag.METEO)
        assertFalse(FeatureFlag.TRAFFIC)
    }

    @Test
    fun productionMapBackends() {
        assertTrue(FeatureFlag.PHONE_MAP_MAPLIBRE)
        assertFalse(FeatureFlag.PHONE_MAP_GOOGLE)
        assertFalse(FeatureFlag.PHONE_MAP_RASTER)
        assertFalse(FeatureFlag.PHONE_MAP_MAPSFORGE)

        assertTrue(FeatureFlag.AA_MAP_MAPSFORGE)
        assertFalse(FeatureFlag.AA_MAP_NATIVE)
        assertFalse(FeatureFlag.AA_MAP_RASTER)
        assertFalse(FeatureFlag.AA_MAP_MAPLIBRE_SNAPSHOTTER)
        assertFalse(FeatureFlag.AA_MAP_MAPLIBRE_VIRTUAL_DISPLAY)
        assertFalse(FeatureFlag.AA_MAP_MAPLIBRE_EGL_DIRECT)
        assertFalse(FeatureFlag.AA_MAP_MAPBOX)
    }

    @Test
    fun setResolvedOverridesAndClearsWhenMatchingDefault() {
        assertFalse(FeatureFlag.RADAR)
        FeatureFlag.setResolved(FeatureFlag.Key.RADAR, true)
        assertTrue(FeatureFlag.RADAR)
        assertTrue(FeatureFlag.isOverridden(FeatureFlag.Key.RADAR))

        FeatureFlag.setResolved(FeatureFlag.Key.RADAR, false)
        assertFalse(FeatureFlag.RADAR)
        assertFalse(FeatureFlag.isOverridden(FeatureFlag.Key.RADAR))
    }

    @Test
    fun countryOverrideEnablesGermany() {
        assertFalse(FeatureFlag.isCountryEnabled("DE"))
        FeatureFlag.setResolved(FeatureFlag.Key.COUNTRY_DE, true)
        assertTrue(FeatureFlag.isCountryEnabled("DE"))
        assertTrue("DE" in FeatureFlag.enabledCountryCodes())
    }
}
