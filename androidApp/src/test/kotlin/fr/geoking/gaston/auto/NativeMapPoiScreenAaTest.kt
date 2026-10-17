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
    fun menuVisibilityToggleClearsAndRestoresList() {
        val screen = newScreen()
        CarScreenTestHarness.createAndStart(screen)

        val template = screen.onGetTemplate() as PlaceListMapTemplate
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

        val hiddenTemplate = screen.onGetTemplate() as PlaceListMapTemplate
        org.junit.Assert.assertNotNull("hidden content list should not be null", hiddenTemplate.itemList)
        org.junit.Assert.assertTrue("hidden itemList should be empty", hiddenTemplate.itemList!!.items.isEmpty())

        // Trigger toggle on again
        visibilityAction.onClickDelegate?.sendClick(dummyCallback)

        val restoredTemplate = screen.onGetTemplate() as PlaceListMapTemplate
        org.junit.Assert.assertNotNull("restored content list should not be null", restoredTemplate.itemList)
    }

    @Test
    fun actionStripStaysWithinMapTemplateLimits() {
        val screen = newScreen()
        CarScreenTestHarness.createAndStart(screen)

        val template = screen.onGetTemplate() as PlaceListMapTemplate
        ActionStripLimits.assertWithinMapTemplateLimits(template.actionStrip)
    }
}
