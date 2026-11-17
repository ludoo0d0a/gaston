package fr.geoking.gaston.auto.map

import androidx.car.app.model.PlaceListMapTemplate
import androidx.test.core.app.ApplicationProvider
import fr.geoking.gaston.FeatureFlag
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.api.belib.BorneAvailabilityProviderFactory
import fr.geoking.gaston.auto.testutil.ActionStripLimits
import fr.geoking.gaston.auto.testutil.CarScreenTestHarness
import fr.geoking.gaston.auto.testutil.FakeBorneAvailabilityProvider
import fr.geoking.gaston.auto.testutil.FakePoiProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * NativeMapPoiScreen has no app-side SurfaceCallback (the host renders Google Maps directly), so the
 * only regression surface here is the PlaceListMapTemplate's ActionStrip staying within host limits.
 */
@RunWith(RobolectricTestRunner::class)
class NativeMapPoiScreenAaTest {

    @After
    fun clearFeatureFlagOverrides() {
        FeatureFlag.clearOverrides()
    }

    private fun newScreen(
        settingsManager: SettingsManager = SettingsManager(ApplicationProvider.getApplicationContext()),
    ): NativeMapPoiScreen {
        val carContext = CarScreenTestHarness.newTestCarContext()
        return NativeMapPoiScreen(
            carContext = carContext,
            poiProvider = FakePoiProvider(),
            availabilityProviderFactory = BorneAvailabilityProviderFactory(belibProvider = FakeBorneAvailabilityProvider()),
            settingsManager = settingsManager,
        )
    }

    @Test
    fun actionStripOmitsRadarTrafficWhenFeatureFlagsOff() {
        val settingsManager = SettingsManager(ApplicationProvider.getApplicationContext())
        val screen = newScreen(settingsManager)
        CarScreenTestHarness.createAndStart(screen)

        settingsManager.setRadarDetectionEnabled(true)
        settingsManager.setTrafficDetectionEnabled(true)
        org.junit.Assert.assertFalse(settingsManager.settings.value.radarWarningEnabled)
        org.junit.Assert.assertFalse(settingsManager.settings.value.mapTrafficEnabled)
        ActionStripLimits.assertWithinMapTemplateLimits(
            (screen.onGetTemplate() as PlaceListMapTemplate).actionStrip,
        )
    }

    @Test
    fun actionStripStaysWithinMapTemplateLimits() {
        val screen = newScreen()
        CarScreenTestHarness.createAndStart(screen)

        val template = screen.onGetTemplate() as PlaceListMapTemplate
        ActionStripLimits.assertWithinMapTemplateLimits(template.actionStrip)
    }

    @Test
    fun actionStripIncludesRefreshWhenRoomAvailable() {
        val screen = newScreen()
        CarScreenTestHarness.createAndStart(screen)

        // Flags off, no cheapest → settings + refresh = 2
        val strip = (screen.onGetTemplate() as PlaceListMapTemplate).actionStrip
        assertEquals(2, strip!!.actions.size)
        ActionStripLimits.assertWithinMapTemplateLimits(strip)
    }

    @Test
    fun actionStripKeepsRefreshWithDetectionTogglesWhenUnderCap() {
        FeatureFlag.setOverride(FeatureFlag.Key.RADAR, true)
        FeatureFlag.setOverride(FeatureFlag.Key.TRAFFIC, true)
        val settingsManager = SettingsManager(ApplicationProvider.getApplicationContext())
        settingsManager.setRadarDetectionEnabled(true)
        settingsManager.setTrafficDetectionEnabled(true)
        val screen = newScreen(settingsManager)
        CarScreenTestHarness.createAndStart(screen)

        // No compass on native: settings + radar + traffic + refresh = 4
        val strip = (screen.onGetTemplate() as PlaceListMapTemplate).actionStrip
        assertEquals(4, strip!!.actions.size)
        ActionStripLimits.assertWithinMapTemplateLimits(strip)
    }
}
