package fr.geoking.gaston.shared.datetime

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class DateTimeUtilsTest {

    @Test
    fun testParseFlexible() {
        assertNotNull(DateTimeUtils.parseFlexible("2024-05-20T10:20:30Z"))
        assertNotNull(DateTimeUtils.parseFlexible("2024-05-20 10:20:30"))
        assertNotNull(DateTimeUtils.parseFlexible("2024-05-20"))
    }

    @Test
    fun testFormatRelativeTime() {
        val now = Clock.System.now()

        val justNow = now.toString()
        assertEquals("just now", DateTimeUtils.formatRelativeTime(justNow))

        val fiveMinutesAgo = (now - 5.minutes).toString()
        assertEquals("5min ago", DateTimeUtils.formatRelativeTime(fiveMinutesAgo))

        val threeHoursAgo = (now - 3.hours).toString()
        assertEquals("3 hours ago", DateTimeUtils.formatRelativeTime(threeHoursAgo))

        val threeHoursTenMinutesAgo = (now - 3.hours - 10.minutes).toString()
        assertEquals("3 hours 10min ago", DateTimeUtils.formatRelativeTime(threeHoursTenMinutesAgo))
    }
}
