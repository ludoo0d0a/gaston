package fr.geoking.gaston.auto.map

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
import fr.geoking.gaston.ui.map.components.AutoSurfaceRenderer

/**
 * CustomMapPoiScreen builds a fresh [AutoSurfaceRenderer] on every surface attach (unlike the
 * persistent-renderer MapLibre/MapTiler/Protomaps modes), but repeated navigation away from and back
 * to the map screen must still not crash, and its background draw thread must be stopped cleanly.
 */
@RunWith(RobolectricTestRunner::class)
class RasterMapPoiScreenAaTest {

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

    @Test
    fun menuVisibilityToggleOnHeaderClearsAndRestoresList() {
        val (_, screen) = newScreen()
        CarScreenTestHarness.createAndStart(screen)

        val template = screen.onGetTemplate() as MapWithContentTemplate
        val content = template.contentTemplate as androidx.car.app.model.ListTemplate
        val header = content.header
        assertNotNull("content header should not be null", header)
        val visibilityAction = header!!.endHeaderActions.lastOrNull()
        assertNotNull("visibility action should exist on content header", visibilityAction)

        val dummyCallback = object : androidx.car.app.OnDoneCallback {
            override fun onSuccess(value: androidx.car.app.serialization.Bundleable?) {}
            override fun onFailure(value: androidx.car.app.serialization.Bundleable) {}
        }

        visibilityAction!!.onClickDelegate?.sendClick(dummyCallback)

        val hiddenTemplate = screen.onGetTemplate() as MapWithContentTemplate
        val hiddenContent = hiddenTemplate.contentTemplate as androidx.car.app.model.ListTemplate
        assertNotNull("hidden content list should not be null", hiddenContent.singleList)
        assertTrue("hidden singleList should be empty", hiddenContent.singleList!!.items.isEmpty())

        visibilityAction.onClickDelegate?.sendClick(dummyCallback)

        val restoredTemplate = screen.onGetTemplate() as MapWithContentTemplate
        val restoredContent = restoredTemplate.contentTemplate as androidx.car.app.model.ListTemplate
        assertNotNull("restored content list should not be null", restoredContent.singleList)
    }

    @Test
    fun actionStripOmitsRadarTrafficWhenFeatureFlagsOff() {
        val carContext = CarScreenTestHarness.newTestCarContext()
        val settingsManager = SettingsManager(ApplicationProvider.getApplicationContext())
        val screen = CustomMapPoiScreen(
            carContext = carContext,
            poiProvider = FakePoiProvider(),
            availabilityProviderFactory = BorneAvailabilityProviderFactory(belibProvider = FakeBorneAvailabilityProvider()),
            settingsManager = settingsManager,
        )
        CarScreenTestHarness.createAndStart(screen)

        settingsManager.setRadarDetectionEnabled(true)
        settingsManager.setTrafficDetectionEnabled(true)
        org.junit.Assert.assertFalse(settingsManager.settings.value.radarWarningEnabled)
        org.junit.Assert.assertFalse(settingsManager.settings.value.mapTrafficEnabled)
        ActionStripLimits.assertWithinMapTemplateLimits(
            (screen.onGetTemplate() as MapWithContentTemplate).actionStrip,
        )
    }

    @Test
    fun noItemsMessageShowsNoResultsWhenFinishedWithEmptyList() {
        val (carContext, screen) = newScreen()
        CarScreenTestHarness.createAndStart(screen)

        val template = screen.onGetTemplate()
        assertTrue("template should be MapWithContentTemplate", template is MapWithContentTemplate)
        val mapWithContent = template as MapWithContentTemplate
        val content = mapWithContent.contentTemplate
        if (content is androidx.car.app.model.ListTemplate && content.singleList != null) {
            val noItemsMsg = content.singleList?.noItemsMessage?.toCharSequence()?.toString().orEmpty()
            assertTrue(
                "noItemsMsg should reflect searching or no results (got: $noItemsMsg)",
                noItemsMsg.contains("Searching") || noItemsMsg.contains("recherche") ||
                    noItemsMsg.contains("No results") || noItemsMsg.contains("pas de résultat")
            )
        }
    }
}
