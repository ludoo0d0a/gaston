package fr.geoking.gaston.auto

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
    fun actionStripStaysWithinMapTemplateLimits() {
        val screen = newScreen()
        CarScreenTestHarness.createAndStart(screen)

        val template = screen.onGetTemplate() as PlaceListMapTemplate
        ActionStripLimits.assertWithinMapTemplateLimits(template.actionStrip)
    }
}
