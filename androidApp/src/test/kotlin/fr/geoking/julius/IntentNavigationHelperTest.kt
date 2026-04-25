package fr.geoking.gaston

import android.content.Intent
import android.net.Uri
import fr.geoking.gaston.intent.IntentNavigationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IntentNavigationHelperTest {

    @Test
    fun `parse geo uri with coords and query`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:48.8566,2.3522?q=Paris"))
        val nav = IntentNavigationHelper.parseNavIntent(intent)!!
        assertEquals(48.8566, nav.latitude!!, 0.0001)
        assertEquals(2.3522, nav.longitude!!, 0.0001)
        assertEquals("Paris", nav.address)
    }

    @Test
    fun `parse geo uri with address only`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=1600+Amphitheatre+Parkway,+Mountain+View,+CA"))
        val nav = IntentNavigationHelper.parseNavIntent(intent)!!
        assertNull(nav.latitude)
        assertNull(nav.longitude)
        assertEquals("1600 Amphitheatre Parkway, Mountain View, CA", nav.address)
    }

    @Test
    fun `parse google navigation uri with coords`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=48.8566,2.3522"))
        val nav = IntentNavigationHelper.parseNavIntent(intent)!!
        assertEquals(48.8566, nav.latitude!!, 0.0001)
        assertEquals(2.3522, nav.longitude!!, 0.0001)
        assertNull(nav.address)
    }

    @Test
    fun `parse google navigation uri with address`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=Paris"))
        val nav = IntentNavigationHelper.parseNavIntent(intent)!!
        assertNull(nav.latitude)
        assertNull(nav.longitude)
        assertEquals("Paris", nav.address)
    }
}
