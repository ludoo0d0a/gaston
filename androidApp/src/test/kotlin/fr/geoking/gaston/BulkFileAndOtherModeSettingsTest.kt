package fr.geoking.gaston

import fr.geoking.gaston.feature.network.BulkFileNetworkAccess
import fr.geoking.gaston.poi.PoiProviderType
import fr.geoking.gaston.shared.network.NetworkType
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
