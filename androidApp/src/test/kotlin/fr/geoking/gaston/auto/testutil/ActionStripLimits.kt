package fr.geoking.gaston.auto.testutil

import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * Host constraints for ActionStrip, per docs/android-auto.md: 2 actions max on non-map templates,
 * 4 max on map-based templates (MapWithContentTemplate, PlaceListMapTemplate...), at most 1 labeled
 * action, and Action.BACK/APP_ICON forbidden (they belong in Header.setStartHeaderAction only).
 */
object ActionStripLimits {

    fun assertWithinMapTemplateLimits(strip: ActionStrip?) = assertWithin(strip, maxActions = 4)

    fun assertWithinNonMapTemplateLimits(strip: ActionStrip?) = assertWithin(strip, maxActions = 2)

    private fun assertWithin(strip: ActionStrip?, maxActions: Int) {
        val actions = strip?.actions ?: return
        assertTrue("ActionStrip should have at most $maxActions actions, had ${actions.size}", actions.size <= maxActions)

        actions.forEach { action ->
            assertFalse("ActionStrip must not contain Action.BACK", action.type == Action.TYPE_BACK)
            assertFalse("ActionStrip must not contain Action.APP_ICON", action.type == Action.TYPE_APP_ICON)
        }

        val labeledCount = actions.count { it.title != null }
        assertTrue("at most 1 labeled action allowed in an ActionStrip, had $labeledCount", labeledCount <= 1)
    }
}
