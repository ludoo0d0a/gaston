package fr.geoking.gaston.shared.datetime

import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

class DateTimeUtilsTest {

    @Test
    fun testParseFlexible() {
        assertNotNull(DateTimeUtils.parseFlexible("2024-05-20T10:20:30Z"))
        assertNotNull(DateTimeUtils.parseFlexible("2024-05-20 10:20:30"))
        assertNotNull(DateTimeUtils.parseFlexible(" 2024-05-20 10:20:30 "))
        assertNotNull(DateTimeUtils.parseFlexible("2024-05-20"))
    }

    @Test
    fun testNormalizeToIso_gasApiSpaceFormat() {
        assertEquals("2026-10-06T00:01:00Z", DateTimeUtils.normalizeToIso("2026-10-06 00:01:00"))
        assertEquals("2026-08-11T00:01:00Z", DateTimeUtils.normalizeToIso("2026-08-11 00:01:00"))
    }

    @Test
    fun testIsOlderThanDays() {
        val now = Clock.System.now()
        val recent = (now - 10.days).toString()
        val stale = (now - 45.days).toString()
        assertFalse(DateTimeUtils.isOlderThanDays(recent, 30, now))
        assertTrue(DateTimeUtils.isOlderThanDays(stale, 30, now))
        assertFalse(DateTimeUtils.isOlderThanDays("not-a-date", 30, now))
    }

    @Test
    fun testFormatDate() {
        fun expected(dateStr: String): String {
            val local = DateTimeUtils.parseFlexible(dateStr)!!
                .toLocalDateTime(TimeZone.currentSystemDefault())
            return "${local.day.toString().padStart(2, '0')}/" +
                "${local.month.number.toString().padStart(2, '0')}/" +
                "${local.year}"
        }
        assertEquals(expected("2026-10-09T12:37:02+00:00"), DateTimeUtils.formatDate("2026-10-09T12:37:02+00:00"))
        assertEquals(expected("2017-09-01 07:01:44"), DateTimeUtils.formatDate("2017-09-01 07:01:44"))
        assertEquals(expected("2026-11-25"), DateTimeUtils.formatDate("2026-11-25"))
        // Zero-padded dd/MM/yyyy (e.g. 25/11/2026)
        assertTrue(Regex("""^\d{2}/\d{2}/\d{4}$""").matches(DateTimeUtils.formatDate("2026-11-25T12:00:00Z")))
        assertEquals("not-a-date", DateTimeUtils.formatDate("not-a-date"))
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

        val twoWeeksAgo = (now - 14.days).toString()
        assertEquals("2 weeks ago", DateTimeUtils.formatRelativeTime(twoWeeksAgo))

        val old = now - 60.days
        assertEquals(DateTimeUtils.formatDate(old.toString()), DateTimeUtils.formatRelativeTime(old.toString()))
    }
}
