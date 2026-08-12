package fr.geoking.gaston.auto

import android.location.Location
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutoMapHeadingTest {

    @Test
    fun normalizeDegrees_wrapsToZeroThreeSixty() {
        assertEquals(90f, AutoMapHeading.normalizeDegrees(450f), 0.001f)
        assertEquals(270f, AutoMapHeading.normalizeDegrees(-90f), 0.001f)
    }

    @Test
    fun effectiveBearing_northUpIsAlwaysZero() {
        assertEquals(0f, AutoMapHeading.effectiveBearing(MapOrientationMode.NorthUp, 120f), 0.001f)
    }

    @Test
    fun effectiveBearing_headingUpUsesHeading() {
        assertEquals(120f, AutoMapHeading.effectiveBearing(MapOrientationMode.HeadingUp, 120f), 0.001f)
    }

    @Test
    fun resolveBearing_usesGpsBearingWhenMoving() {
        val location = Location("test").apply {
            latitude = 48.0
            longitude = 2.0
            bearing = 45f
            speed = 5f
        }
        assertEquals(45f, AutoMapHeading.resolveBearing(location, 0f), 0.001f)
    }

    @Test
    fun resolveBearing_keepsLastWhenStationary() {
        val location = Location("test").apply {
            latitude = 48.0
            longitude = 2.0
            bearing = 45f
            speed = 0.2f
        }
        assertEquals(10f, AutoMapHeading.resolveBearing(location, 10f), 0.001f)
    }

    @Test
    fun shortestAngleDelta_acrossZero() {
        assertEquals(-20f, AutoMapHeading.shortestAngleDelta(350f, 330f), 0.001f)
        assertEquals(20f, AutoMapHeading.shortestAngleDelta(10f, 30f), 0.001f)
    }

    @Test
    fun modeAfterBearingChange_entersHeadingUpFromNorthUpWhenNonZero() {
        assertEquals(
            MapOrientationMode.HeadingUp,
            AutoMapHeading.modeAfterBearingChange(MapOrientationMode.NorthUp, 10f),
        )
    }

    @Test
    fun modeAfterBearingChange_keepsHeadingUpWhenBearingReturnsToZero() {
        // Regression: flipping to NorthUp at 0° moves the follow focal point and pans.
        assertEquals(
            MapOrientationMode.HeadingUp,
            AutoMapHeading.modeAfterBearingChange(MapOrientationMode.HeadingUp, 0f),
        )
        assertEquals(
            MapOrientationMode.HeadingUp,
            AutoMapHeading.modeAfterBearingChange(MapOrientationMode.HeadingUp, 10f),
        )
    }

    @Test
    fun modeAfterBearingChange_keepsNorthUpWhenBearingStaysZero() {
        assertEquals(
            MapOrientationMode.NorthUp,
            AutoMapHeading.modeAfterBearingChange(MapOrientationMode.NorthUp, 0f),
        )
    }

    @Test
    fun effectiveBearing_headingUpAtZeroStillZero_withoutChangingFocalSemantics() {
        assertEquals(0f, AutoMapHeading.effectiveBearing(MapOrientationMode.HeadingUp, 0f), 0.001f)
    }
}
