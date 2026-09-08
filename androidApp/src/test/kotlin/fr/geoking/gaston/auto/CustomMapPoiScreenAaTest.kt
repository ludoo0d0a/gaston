package fr.geoking.gaston.auto

import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.test.core.app.ApplicationProvider
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.api.belib.BorneAvailabilityProviderFactory
import fr.geoking.gaston.auto.testutil.ActionStripLimits
import fr.geoking.gaston.auto.testutil.CarScreenTestHarness
import fr.geoking.gaston.auto.testutil.FakeBorneAvailabilityProvider
import fr.geoking.gaston.auto.testutil.FakePoiProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * CustomMapPoiScreen builds a fresh [AutoSurfaceRenderer] on every surface attach (unlike the
 * persistent-renderer MapLibre/MapTiler/Protomaps modes), but repeated navigation away from and back
 * to the map screen must still not crash, and its background draw thread must be stopped cleanly.
 */
@RunWith(RobolectricTestRunner::class)
class CustomMapPoiScreenAaTest {

    private fun newScreen(): Pair<androidx.car.app.testing.TestCarContext, CustomMapPoiScreen> {
        val carContext = CarScreenTestHarness.newTestCarContext()
        val screen = CustomMapPoiScreen(
            carContext = carContext,
            poiProvider = FakePoiProvider(),
            availabilityProviderFactory = BorneAvailabilityProviderFactory(belibProvider = FakeBorneAvailabilityProvider()),
            settingsManager = SettingsManager(ApplicationProvider.getApplicationContext()),
        )
        return carContext to screen
    }

    @Test
    fun customMapSurvivesSurfaceDetachReattach() {
        val (carContext, screen) = newScreen()
        CarScreenTestHarness.createAndStart(screen)

        val surfaceCallback = CarScreenTestHarness.surfaceCallbackOf(carContext)
        assertNotNull("CustomMapPoiScreen must register a SurfaceCallback once started", surfaceCallback)

        val container = CarScreenTestHarness.newFakeSurfaceContainer()
        surfaceCallback!!.onSurfaceAvailable(container)
        assertNotNull("a renderer should be created on first attach", screen.surfaceRendererForTest)

        surfaceCallback.onSurfaceDestroyed(container)
        surfaceCallback.onSurfaceAvailable(container)
        assertNotNull("a fresh renderer should be created on reattach", screen.surfaceRendererForTest)

        // Always leave the renderer's background draw thread stopped/joined.
        surfaceCallback.onSurfaceDestroyed(container)
    }

    @Test
    fun actionStripsStayWithinMapTemplateLimits() {
        val (_, screen) = newScreen()
        CarScreenTestHarness.createAndStart(screen)

        val template = screen.onGetTemplate() as MapWithContentTemplate
        ActionStripLimits.assertWithinMapTemplateLimits(template.actionStrip)
        ActionStripLimits.assertWithinMapTemplateLimits(template.mapController?.mapActionStrip)
    }
}
