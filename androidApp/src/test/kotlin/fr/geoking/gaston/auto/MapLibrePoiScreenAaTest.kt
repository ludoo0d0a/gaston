package fr.geoking.gaston.auto

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Regression guard for the recurring AA "maps are crashing" bug: [CarMapLibreRenderer] keeps a single
 * persistent renderer per screen instance, reused across surface attach/detach cycles (navigating away
 * from and back to the map). If an EGL surface is ever attached to the same Surface this renderer locks
 * via Canvas, the second [android.view.Surface.lockHardwareCanvas]/[android.view.Surface.lockCanvas] call
 * throws "Surface was already connected to another API". This covers all three CanvasMapModeConfig
 * variants (MapLibre, MapTiler, Protomaps) since they share this exact renderer/screen wiring.
 */
@RunWith(RobolectricTestRunner::class)
class MapLibrePoiScreenAaTest {

    // CarMapLibreRenderer reaches into the global Koin container for its own SettingsManager
    // instance (fr.geoking.gaston.auto.maplibre.CarMapLibreRenderer.kt:56) instead of using the one
    // passed into MapLibrePoiScreen's constructor — a real, separate coupling from the constructor
    // injection the Screen classes otherwise use. A minimal Koin app must be running for any of these
    // screens to construct successfully.
    @Before
    fun startTestKoin() {
        AaSurfaceSession.container = null
        AaSurfaceSession.visibleArea = null
        startKoin {
            modules(module { single { SettingsManager(ApplicationProvider.getApplicationContext()) } })
        }
    }

    @After
    fun stopTestKoin() {
        AaSurfaceSession.container = null
        AaSurfaceSession.visibleArea = null
        stopKoin()
    }

    private fun newSettingsManager(): SettingsManager =
        SettingsManager(ApplicationProvider.getApplicationContext())

    private fun newScreen(carContext: TestCarContext, config: CanvasMapModeConfig, settingsManager: SettingsManager): MapLibrePoiScreen =
        MapLibrePoiScreen(
            carContext = carContext,
            poiProvider = FakePoiProvider(),
            availabilityProviderFactory = BorneAvailabilityProviderFactory(belibProvider = FakeBorneAvailabilityProvider()),
            settingsManager = settingsManager,
            canvasMapModeConfig = config,
        )

    private fun assertSurfaceSurvivesDetachReattach(config: CanvasMapModeConfig) {
        val carContext = CarScreenTestHarness.newTestCarContext()
        val screen = newScreen(carContext, config, newSettingsManager())
        CarScreenTestHarness.createAndStart(screen)

        val surfaceCallback = CarScreenTestHarness.surfaceCallbackOf(carContext)
        assertTrue("MapLibrePoiScreen must register a SurfaceCallback once started", surfaceCallback != null)

        val container = CarScreenTestHarness.newFakeSurfaceContainer()
        // Navigate to the map, away from it, then back — the exact sequence that reproduces the
        // EGL/Canvas contention bug if the renderer's attachSurface() ever regresses.
        surfaceCallback!!.onSurfaceAvailable(container)
        surfaceCallback.onSurfaceDestroyed(container)
        surfaceCallback.onSurfaceAvailable(container)
    }

    @Test
    fun mapLibreSurvivesSurfaceDetachReattach() {
        val carContext = CarScreenTestHarness.newTestCarContext()
        assertSurfaceSurvivesDetachReattach(CanvasMapModeConfig.mapLibreSnapshotter(carContext))
    }

    @Test
    fun mapLibreVirtualDisplaySurvivesSurfaceDetachReattach() {
        val carContext = CarScreenTestHarness.newTestCarContext()
        assertSurfaceSurvivesDetachReattach(CanvasMapModeConfig.mapLibreVirtualDisplay(carContext))
    }

    @Test
    fun menuVisibilityToggleClearsAndRestoresList() {
        val carContext = CarScreenTestHarness.newTestCarContext()
        val screen = newScreen(carContext, CanvasMapModeConfig.mapLibreSnapshotter(carContext), newSettingsManager())
        CarScreenTestHarness.createAndStart(screen)

        val template = screen.onGetTemplate() as MapWithContentTemplate
        val actionStrip = template.actionStrip
        org.junit.Assert.assertNotNull("actionStrip should not be null", actionStrip)

        val visibilityAction = actionStrip!!.actions.firstOrNull { action ->
            action.icon != null
        }
        org.junit.Assert.assertNotNull("visibility action should exist on ActionStrip", visibilityAction)

        val dummyCallback = object : androidx.car.app.OnDoneCallback {
            override fun onSuccess(value: androidx.car.app.serialization.Bundleable?) {}
            override fun onFailure(value: androidx.car.app.serialization.Bundleable) {}
        }

        // Trigger toggle off
        visibilityAction!!.onClickDelegate?.sendClick(dummyCallback)

        val hiddenTemplate = screen.onGetTemplate() as MapWithContentTemplate
        val hiddenContent = hiddenTemplate.contentTemplate as androidx.car.app.model.ListTemplate
        org.junit.Assert.assertNotNull("hidden content list should not be null", hiddenContent.singleList)
        assertTrue("hidden singleList should be empty", hiddenContent.singleList!!.items.isEmpty())

        // Trigger toggle on again
        visibilityAction.onClickDelegate?.sendClick(dummyCallback)

        val restoredTemplate = screen.onGetTemplate() as MapWithContentTemplate
        val restoredContent = restoredTemplate.contentTemplate as androidx.car.app.model.ListTemplate
        org.junit.Assert.assertNotNull("restored content list should not be null", restoredContent.singleList)
    }

    @Test
    fun actionStripsStayWithinMapTemplateLimits() {
        val carContext = CarScreenTestHarness.newTestCarContext()
        val screen = newScreen(carContext, CanvasMapModeConfig.mapLibreSnapshotter(carContext), newSettingsManager())
        CarScreenTestHarness.createAndStart(screen)

        val template = screen.onGetTemplate() as MapWithContentTemplate
        ActionStripLimits.assertWithinMapTemplateLimits(template.actionStrip)
        ActionStripLimits.assertWithinMapTemplateLimits(template.mapController?.mapActionStrip)
    }
}
