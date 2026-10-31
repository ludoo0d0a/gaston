package fr.geoking.gaston.auto.map

import androidx.car.app.model.PlaceListMapTemplate
import androidx.test.core.app.ApplicationProvider
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.api.belib.BorneAvailabilityProviderFactory
import fr.geoking.gaston.auto.testutil.ActionStripLimits
import fr.geoking.gaston.auto.testutil.CarScreenTestHarness
import fr.geoking.gaston.auto.testutil.FakeBorneAvailabilityProvider
import fr.geoking.gaston.auto.testutil.FakePoiProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * NativeMapPoiScreen has no app-side SurfaceCallback (the host renders Google Maps directly), so the
 * only regression surface here is the PlaceListMapTemplate's ActionStrip staying within host limits.
 */
@RunWith(RobolectricTestRunner::class)
class NativeMapPoiScreenAaTest {

    private fun newScreen(): NativeMapPoiScreen {
        val carContext = CarScreenTestHarness.newTestCarContext()
        return NativeMapPoiScreen(
            carContext = carContext,
            poiProvider = FakePoiProvider(),
            availabilityProviderFactory = BorneAvailabilityProviderFactory(belibProvider = FakeBorneAvailabilityProvider()),
            settingsManager = SettingsManager(ApplicationProvider.getApplicationContext()),
        )
    }

    @Test
    fun actionStripRadarTrafficTogglesDetectionSettings() {
        val carContext = CarScreenTestHarness.newTestCarContext()
        val settingsManager = SettingsManager(ApplicationProvider.getApplicationContext())
        val screen = NativeMapPoiScreen(
            carContext = carContext,
            poiProvider = FakePoiProvider(),
            availabilityProviderFactory = BorneAvailabilityProviderFactory(belibProvider = FakeBorneAvailabilityProvider()),
            settingsManager = settingsManager,
        )
        CarScreenTestHarness.createAndStart(screen)

        val actions = (screen.onGetTemplate() as PlaceListMapTemplate).actionStrip!!.actions
        org.junit.Assert.assertTrue("strip should include radar + traffic", actions.size >= 2)
        val radarAction = actions[actions.size - 2]
        val trafficAction = actions[actions.size - 1]

        val dummyCallback = object : androidx.car.app.OnDoneCallback {
            override fun onSuccess(value: androidx.car.app.serialization.Bundleable?) {}
            override fun onFailure(value: androidx.car.app.serialization.Bundleable) {}
        }

        org.junit.Assert.assertFalse(settingsManager.settings.value.radarWarningEnabled)
        radarAction.onClickDelegate?.sendClick(dummyCallback)
        org.junit.Assert.assertTrue(settingsManager.settings.value.radarWarningEnabled)
        org.junit.Assert.assertTrue("speed_camera" in settingsManager.settings.value.selectedOverpassAmenityTypes)

        org.junit.Assert.assertFalse(settingsManager.settings.value.mapTrafficEnabled)
        trafficAction.onClickDelegate?.sendClick(dummyCallback)
        org.junit.Assert.assertTrue(settingsManager.settings.value.mapTrafficEnabled)
        org.junit.Assert.assertTrue(settingsManager.settings.value.trafficAlertEnabled)
    }

    @Test
    fun actionStripStaysWithinMapTemplateLimits() {
        val screen = newScreen()
        CarScreenTestHarness.createAndStart(screen)

        val template = screen.onGetTemplate() as PlaceListMapTemplate
        ActionStripLimits.assertWithinMapTemplateLimits(template.actionStrip)
    }
}
