package fr.geoking.gaston

import fr.geoking.gaston.feature.network.BulkFileNetworkAccess
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProviderType
import fr.geoking.gaston.shared.network.NetworkType
import fr.geoking.gaston.effectiveAllowedCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class BulkFileAndOtherModeSettingsTest {

    @Test
    fun isOtherModeActive_withLufopStillOther() {
        val settings = AppSettings(
            poiProviderSelectionMode = PoiProviderSelectionMode.Manual,
            selectedPoiProviders = setOf(
                PoiProviderType.Overpass,
                PoiProviderType.LufopOpenSpeedCam,
            ),
            selectedOverpassAmenityTypes = setOf("speed_camera"),
        )
        assertTrue(settings.isOtherModeActive())
    }

    @Test
    fun setOverpassAmenityTypes_speedCameraEnablesLufopProviders() {
        val manager = SettingsManager(RuntimeEnvironment.getApplication(), firestoreSync = null)
        // Force Other mode with parking only (phone default).
        manager.setOtherMode("parking")
        assertEquals(setOf(PoiProviderType.Overpass), manager.settings.value.selectedPoiProviders)

        manager.setOverpassAmenityTypes(setOf("speed_camera"))
        val after = manager.settings.value
        assertTrue(PoiProviderType.LufopOpenSpeedCam in after.selectedPoiProviders)
        assertTrue(PoiProviderType.Overpass in after.selectedPoiProviders)
        assertEquals(setOf("speed_camera"), after.selectedOverpassAmenityTypes)
    }

    @Test
    fun setOtherMode_speedCameraEnablesLufopAndOverpass() {
        val manager = SettingsManager(RuntimeEnvironment.getApplication(), firestoreSync = null)
        manager.setOtherMode("speed_camera")
        val after = manager.settings.value
        assertTrue(after.isOtherModeActive())
        assertEquals(setOf("speed_camera"), after.selectedOverpassAmenityTypes)
        assertTrue(PoiProviderType.LufopOpenSpeedCam in after.selectedPoiProviders)
        assertTrue(PoiProviderType.Overpass in after.selectedPoiProviders)
        assertTrue(PoiProviderType.LuxembourgRadars in after.selectedPoiProviders)
    }

    @Test
    fun setOtherMode_weatherEnablesOpenMeteoWeatherOnly() {
        val manager = SettingsManager(RuntimeEnvironment.getApplication(), firestoreSync = null)
        manager.setOtherMode("weather")
        val after = manager.settings.value
        assertTrue(after.isOtherModeActive())
        assertEquals(setOf("weather"), after.selectedOverpassAmenityTypes)
        assertEquals(setOf(PoiProviderType.OpenMeteoWeather), after.selectedPoiProviders)
        assertEquals(PoiCategory.Weather, categoryFromAmenityId("weather"))
        assertEquals(setOf(PoiCategory.Weather), after.effectiveAllowedCategories())
        assertFalse(after.mapTrafficEnabled)
    }

    @Test
    fun leavingWeatherOtherMode_doesNotKeepWeatherInCacheWarmAmenityTypes() {
        val manager = SettingsManager(RuntimeEnvironment.getApplication(), firestoreSync = null)
        manager.setOtherMode("weather")
        manager.setEnergyFilterMode(PoiCategory.Gas.let { fr.geoking.gaston.poi.EnergyFilterMode.Fuel })
        val after = manager.settings.value
        assertFalse("weather" in after.cacheWarmAmenityTypes)
    }

    @Test
    fun setOtherMode_speedCameraAllowsOnlyRadarCategory() {
        val manager = SettingsManager(RuntimeEnvironment.getApplication(), firestoreSync = null)
        manager.setMapTrafficEnabled(true)
        manager.setOtherMode("speed_camera")
        val after = manager.settings.value
        assertEquals(setOf(PoiCategory.Radar), after.effectiveAllowedCategories())
        assertFalse(after.mapTrafficEnabled)
    }

    @Test
    fun setTrafficMode_enablesTrafficAndHidesAmenityPois() {
        val manager = SettingsManager(RuntimeEnvironment.getApplication(), firestoreSync = null)
        manager.setOtherMode("speed_camera")
        manager.setTrafficMode()
        val after = manager.settings.value
        assertTrue(after.isOtherModeActive())
        assertTrue(after.mapTrafficEnabled)
        assertTrue(after.selectedOverpassAmenityTypes.isEmpty())
        assertEquals(setOf(PoiCategory.Traffic), after.effectiveAllowedCategories())
    }

    @Test
    fun setOverpassAmenityTypes_clearsTrafficOverlay() {
        val manager = SettingsManager(RuntimeEnvironment.getApplication(), firestoreSync = null)
        manager.setTrafficMode()
        assertTrue(manager.settings.value.mapTrafficEnabled)
        manager.setOverpassAmenityTypes(setOf("parking"))
        val after = manager.settings.value
        assertFalse(after.mapTrafficEnabled)
        assertEquals(setOf("parking"), after.selectedOverpassAmenityTypes)
    }

    @Test
    fun isOtherModeActive_fuelProviderExitsOther() {
        val settings = AppSettings(
            poiProviderSelectionMode = PoiProviderSelectionMode.Manual,
            selectedPoiProviders = setOf(PoiProviderType.Etalab, PoiProviderType.LufopOpenSpeedCam),
        )
        assertFalse(settings.isOtherModeActive())
    }

    @Test
    fun bulkFilePolicy_neverBlocksAllNetworks() {
        val settings = AppSettings(bulkFileDownloadPolicy = BulkFileDownloadPolicy.Never)
        assertFalse(BulkFileNetworkAccess.allowFetch(settings, NetworkType.WIFI))
        assertFalse(BulkFileNetworkAccess.allowFetch(settings, NetworkType.FOUR_G))
    }

    @Test
    fun bulkFilePolicy_wifiOnlyAllowsWifi() {
        val settings = AppSettings(bulkFileDownloadPolicy = BulkFileDownloadPolicy.WifiOnly)
        assertTrue(BulkFileNetworkAccess.allowFetch(settings, NetworkType.WIFI))
        assertFalse(BulkFileNetworkAccess.allowFetch(settings, NetworkType.FOUR_G))
    }

    @Test
    fun bulkFilePolicy_allowedOnAnyNetwork() {
        val settings = AppSettings(bulkFileDownloadPolicy = BulkFileDownloadPolicy.Allowed)
        assertTrue(BulkFileNetworkAccess.allowFetch(settings, NetworkType.WIFI))
        assertTrue(BulkFileNetworkAccess.allowFetch(settings, NetworkType.FOUR_G))
    }

    @Test
    fun bulkFilePolicy_defaultIsNever() {
        assertEquals(BulkFileDownloadPolicy.Never, AppSettings().bulkFileDownloadPolicy)
    }
}
