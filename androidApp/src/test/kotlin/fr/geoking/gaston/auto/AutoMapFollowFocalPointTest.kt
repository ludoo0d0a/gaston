package fr.geoking.gaston.auto

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutoMapFollowFocalPointTest {

    @Test
    fun focalPoint_nullVisibleArea_usesFullSurfaceMidpoint_whenNorthUp() {
        val focal = AutoMapFollowFocalPoint.focalPointPx(
            visibleArea = null,
            surfaceWidth = 800,
            surfaceHeight = 480,
            headingUp = false,
        )
        assertEquals(400.0, focal.x, 0.001)
        assertEquals(240.0, focal.y, 0.001)
    }

    @Test
    fun focalPoint_nullVisibleArea_lookAheadY_whenHeadingUp() {
        val focal = AutoMapFollowFocalPoint.focalPointPx(
            visibleArea = null,
            surfaceWidth = 800,
            surfaceHeight = 480,
            headingUp = true,
        )
        assertEquals(400.0, focal.x, 0.001)
        // 1/3 from bottom: top + 2/3 * height = 320
        assertEquals(320.0, focal.y, 0.001)
    }

    @Test
    fun focalPoint_menuOnLeft_centersInRemainingVisible_northUp() {
        // Menu 0..200, map 200..800
        val visible = Rect(200, 0, 800, 480)
        val focal = AutoMapFollowFocalPoint.focalPointPx(
            visibleArea = visible,
            surfaceWidth = 800,
            surfaceHeight = 480,
            headingUp = false,
        )
        assertEquals(500.0, focal.x, 0.001)
        assertEquals(240.0, focal.y, 0.001)
    }

    @Test
    fun focalPoint_menuOnRight_centersInRemainingVisible_headingUp() {
        // Map 0..600, menu 600..800
        val visible = Rect(0, 40, 600, 480)
        val focal = AutoMapFollowFocalPoint.focalPointPx(
            visibleArea = visible,
            surfaceWidth = 800,
            surfaceHeight = 480,
            headingUp = true,
        )
        assertEquals(300.0, focal.x, 0.001)
        // top + 2/3 * (480-40) = 40 + 2/3 * 440 = 40 + 293.333...
        assertEquals(40.0 + 440.0 * 2.0 / 3.0, focal.y, 0.001)
    }

    @Test
    fun mapLibrePadding_northUp_matchesVisibleInsets() {
        val visible = Rect(200, 20, 800, 460)
        val padding = AutoMapFollowFocalPoint.mapLibrePadding(
            visibleArea = visible,
            surfaceWidth = 800,
            surfaceHeight = 480,
            headingUp = false,
        )
        assertEquals(200, padding.left)
        assertEquals(20, padding.top)
        assertEquals(0, padding.right)
        assertEquals(20, padding.bottom)
    }

    @Test
    fun mapLibrePadding_headingUp_addsTopThirdOfVisibleHeight() {
        val visible = Rect(0, 0, 600, 480)
        val padding = AutoMapFollowFocalPoint.mapLibrePadding(
            visibleArea = visible,
            surfaceWidth = 800,
            surfaceHeight = 480,
            headingUp = true,
        )
        assertEquals(0, padding.left)
        assertEquals(160, padding.top) // 0 + 480/3
        assertEquals(200, padding.right) // 800 - 600
        assertEquals(0, padding.bottom)
    }

    @Test
    fun focalPoint_unchangedWhenHeadingUpBearingGoesToZero() {
        // Bearing 10° → 0° must not move the pivot (user location) on screen.
        val at10 = AutoMapFollowFocalPoint.focalPointPx(
            visibleArea = null,
            surfaceWidth = 800,
            surfaceHeight = 480,
            headingUp = true,
        )
        val at0 = AutoMapFollowFocalPoint.focalPointPx(
            visibleArea = null,
            surfaceWidth = 800,
            surfaceHeight = 480,
            headingUp = true, // still HeadingUp; AA does not flip to NorthUp at 0°
        )
        assertEquals(at10.x, at0.x, 0.001)
        assertEquals(at10.y, at0.y, 0.001)
        // Contrast: NorthUp would jump the pivot to surface center (pan).
        val northUp = AutoMapFollowFocalPoint.focalPointPx(
            visibleArea = null,
            surfaceWidth = 800,
            surfaceHeight = 480,
            headingUp = false,
        )
        assertEquals(240.0, northUp.y, 0.001)
        assertEquals(320.0, at0.y, 0.001)
    }
}
