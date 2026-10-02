package fr.geoking.gaston

import fr.geoking.gaston.feature.network.BulkFileNetworkAccess
import fr.geoking.gaston.poi.PoiProviderType
import fr.geoking.gaston.shared.network.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
