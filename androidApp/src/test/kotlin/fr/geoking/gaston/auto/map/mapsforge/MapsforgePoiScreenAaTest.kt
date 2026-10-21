package fr.geoking.gaston.auto.map.mapsforge

import androidx.car.app.OnDoneCallback
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.ListTemplate
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.car.app.serialization.Bundleable
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.api.belib.BorneAvailabilityProviderFactory
import fr.geoking.gaston.auto.mapsforge.MapsforgePoiScreen
import fr.geoking.gaston.auto.testutil.ActionStripLimits
import fr.geoking.gaston.auto.testutil.CarScreenTestHarness
import fr.geoking.gaston.auto.testutil.FakeBorneAvailabilityProvider
import fr.geoking.gaston.auto.testutil.FakePoiProvider
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * MapsforgePoiScreen builds a fresh [fr.geoking.gaston.auto.mapsforge.CarMapsforgeRenderer] on every surface attach, and that renderer
 * spins up a real background draw thread. Unlike Protomaps, this mode has no offline-unavailable banner
 * wiring today — a missing .map file falls back to online raster rendering instead (see
 * CarMapsforgeRenderer's "using online raster fallback" log path) — so this suite only asserts that
 * behavior, not a banner that doesn't exist in code.
 */
@RunWith(RobolectricTestRunner::class)
class MapsforgePoiScreenAaTest {

    private var surfaceCallback: SurfaceCallback? = null
    private var container: SurfaceContainer? = null

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
    fun menuVisibilityToggleClearsAndRestoresList() {
        val (_, screen) = newScreen()
        CarScreenTestHarness.createAndStart(screen)

        val template = screen.onGetTemplate() as MapWithContentTemplate
        val actionStrip = template.actionStrip
        assertNotNull("actionStrip should not be null", actionStrip)

        val visibilityAction = actionStrip!!.actions.firstOrNull { action ->
            action.icon != null
        }
        assertNotNull("visibility action should exist on ActionStrip", visibilityAction)

        val dummyCallback = object : OnDoneCallback {
            override fun onSuccess(value: Bundleable?) {}
            override fun onFailure(value: Bundleable) {}
        }

        // Trigger toggle off
        visibilityAction!!.onClickDelegate?.sendClick(dummyCallback)

        val hiddenTemplate = screen.onGetTemplate() as MapWithContentTemplate
        val hiddenContent = hiddenTemplate.contentTemplate as ListTemplate
        assertNotNull("hidden content list should not be null", hiddenContent.singleList)
        Assert.assertTrue("hidden singleList should be empty", hiddenContent.singleList!!.items.isEmpty())

        // Trigger toggle on again
        visibilityAction.onClickDelegate?.sendClick(dummyCallback)

        val restoredTemplate = screen.onGetTemplate() as MapWithContentTemplate
        val restoredContent = restoredTemplate.contentTemplate as ListTemplate
        assertNotNull("restored content list should not be null", restoredContent.singleList)
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
