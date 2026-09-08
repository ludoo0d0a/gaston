package fr.geoking.gaston.auto.mapsforge

import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.api.belib.BorneAvailabilityProviderFactory
import fr.geoking.gaston.auto.testutil.ActionStripLimits
import fr.geoking.gaston.auto.testutil.CarScreenTestHarness
import fr.geoking.gaston.auto.testutil.FakeBorneAvailabilityProvider
import fr.geoking.gaston.auto.testutil.FakePoiProvider
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * MapsforgePoiScreen builds a fresh [CarMapsforgeRenderer] on every surface attach, and that renderer
 * spins up a real background draw thread. Unlike Protomaps, this mode has no offline-unavailable banner
 * wiring today — a missing .map file falls back to online raster rendering instead (see
 * CarMapsforgeRenderer's "using online raster fallback" log path) — so this suite only asserts that
 * behavior, not a banner that doesn't exist in code.
 */
@RunWith(RobolectricTestRunner::class)
class MapsforgePoiScreenAaTest {

    private var surfaceCallback: androidx.car.app.SurfaceCallback? = null
    private var container: androidx.car.app.SurfaceContainer? = null

    private fun newScreen(): Pair<TestCarContext, MapsforgePoiScreen> {
        val carContext = CarScreenTestHarness.newTestCarContext()
        val screen = MapsforgePoiScreen(
            carContext = carContext,
            poiProvider = FakePoiProvider(),
            availabilityProviderFactory = BorneAvailabilityProviderFactory(belibProvider = FakeBorneAvailabilityProvider()),
            settingsManager = SettingsManager(ApplicationProvider.getApplicationContext()),
        )
        return carContext to screen
    }

    @After
    fun stopAnyRunningRenderer() {
        // The renderer's draw thread must be joined even if a test assertion fails mid-way, or it
        // leaks into the shared JVM test process.
        surfaceCallback?.let { callback -> container?.let { callback.onSurfaceDestroyed(it) } }
        surfaceCallback = null
        container = null
    }

    @Test
    fun mapsforgeSurvivesSurfaceDetachReattachWithNoOfflineFile() {
        val (carContext, screen) = newScreen()
        CarScreenTestHarness.createAndStart(screen)

        surfaceCallback = CarScreenTestHarness.surfaceCallbackOf(carContext)
        assertNotNull("MapsforgePoiScreen must register a SurfaceCallback once started", surfaceCallback)
        container = CarScreenTestHarness.newFakeSurfaceContainer()

        // No offlineMapsforgePath configured: renderer must fall back gracefully, not crash.
        surfaceCallback!!.onSurfaceAvailable(container!!)
        surfaceCallback!!.onSurfaceDestroyed(container!!)
        surfaceCallback!!.onSurfaceAvailable(container!!)
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
