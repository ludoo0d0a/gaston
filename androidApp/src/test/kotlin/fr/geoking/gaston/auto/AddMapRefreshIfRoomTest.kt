package fr.geoking.gaston.auto

import androidx.car.app.model.ActionStrip
import fr.geoking.gaston.auto.testutil.CarScreenTestHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AddMapRefreshIfRoomTest {

    @Test
    fun addsRefreshWhenUnderMapActionStripCap() {
        val carContext = CarScreenTestHarness.newTestCarContext()
        val builder = ActionStrip.Builder()
        val added = builder.addMapRefreshIfRoom(carContext, currentActionCount = 3) {}
        assertTrue(added)
        assertEquals(1, builder.build().actions.size)
    }

    @Test
    fun omitsRefreshWhenMapActionStripAlreadyFull() {
        val carContext = CarScreenTestHarness.newTestCarContext()
        val builder = ActionStrip.Builder()
        val added = builder.addMapRefreshIfRoom(carContext, currentActionCount = 4) {}
        assertFalse(added)
        // Builder with zero actions cannot build; only assert the helper returned false.
    }
}
