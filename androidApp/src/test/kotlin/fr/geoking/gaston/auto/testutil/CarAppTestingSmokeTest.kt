package fr.geoking.gaston.auto.testutil

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.car.app.testing.ScreenController
import androidx.car.app.testing.TestCarContext
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Confirms androidx.car.app:app-testing's TestCarContext/ScreenController behave as expected
 * under this project's Robolectric setup, before the real AA screen tests build on top of them.
 */
@RunWith(RobolectricTestRunner::class)
class CarAppTestingSmokeTest {

    private class NoOpScreen(carContext: CarContext) : Screen(carContext) {
        override fun onGetTemplate(): Template =
            MessageTemplate.Builder("smoke").build()
    }

    @Test
    fun screenControllerDrivesARealScreenToATemplate() {
        val carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
        val screenController = ScreenController(NoOpScreen(carContext))

        screenController.moveToState(Lifecycle.State.RESUMED)

        val template = screenController.screen.onGetTemplate()
        assertEquals(MessageTemplate::class.java, template.javaClass)
    }
}
