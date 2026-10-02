package fr.geoking.gaston.feature.notification

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InAppNotificationCenterTest {

    private fun center(): InAppNotificationCenter {
        val scope = TestScope(UnconfinedTestDispatcher())
        return InAppNotificationCenter(maxItems = 3, scope = scope)
    }

    @Test
    fun add_prependsAndCapsSize() {
        val c = center()
        c.add("t1", "m1")
        c.add("t2", "m2")
        c.add("t3", "m3")
        c.add("t4", "m4")
        assertEquals(listOf("t4", "t3", "t2"), c.notifications.value.map { it.title })
    }

    @Test
    fun add_withActionUrl_keepsUrl() {
        val c = center()
        val item = c.add("Border", "Vignette needed", actionUrl = "https://shop.asfinag.at/")
        assertEquals("https://shop.asfinag.at/", item.actionUrl)
        assertFalse(item.read)
        assertEquals(1, c.unreadCount.value)
    }

    @Test
    fun markAllRead_clearsUnreadBadge() {
        val c = center()
        c.add("a", "1")
        c.add("b", "2")
        assertEquals(2, c.unreadCount.value)
        c.markAllRead()
        assertEquals(0, c.unreadCount.value)
        assertTrue(c.notifications.value.all { it.read })
    }

    @Test
    fun blankActionUrl_storedAsNull() {
        val c = center()
        val item = c.add("a", "b", actionUrl = "  ")
        assertNull(item.actionUrl)
    }
}
