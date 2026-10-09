package fr.geoking.gaston.parked

import org.junit.Assert.assertEquals
import org.junit.Test

class InSessionStopTrackerTest {

    @Test
    fun doesNotConfirmBeforeDriving() {
        val tracker = InSessionStopTracker()
        assertEquals(
            InSessionStopTracker.Result.Continue,
            tracker.onSample(speedMps = 0f, elapsedMs = 20_000L),
        )
    }

    @Test
    fun confirmsAfterDriveThen10sStationary() {
        val tracker = InSessionStopTracker()
        assertEquals(
            InSessionStopTracker.Result.Continue,
            tracker.onSample(speedMps = 20f / 3.6f, elapsedMs = 0L),
        )
        // Stationary window starts here
        assertEquals(
            InSessionStopTracker.Result.Continue,
            tracker.onSample(speedMps = 0f, elapsedMs = 1_000L),
        )
        assertEquals(
            InSessionStopTracker.Result.Continue,
            tracker.onSample(speedMps = null, elapsedMs = 10_000L),
        )
        assertEquals(
            InSessionStopTracker.Result.Confirmed,
            tracker.onSample(speedMps = null, elapsedMs = 11_000L),
        )
    }

    @Test
    fun vehicleSpeedResetsStationaryWindow() {
        val tracker = InSessionStopTracker()
        tracker.onSample(speedMps = 20f / 3.6f, elapsedMs = 0L)
        tracker.onSample(speedMps = 0f, elapsedMs = 8_000L)
        // Drive again before 10s
        assertEquals(
            InSessionStopTracker.Result.Continue,
            tracker.onSample(speedMps = 20f / 3.6f, elapsedMs = 9_000L),
        )
        // New stationary window starts at 14s
        assertEquals(
            InSessionStopTracker.Result.Continue,
            tracker.onSample(speedMps = 0f, elapsedMs = 14_000L),
        )
        assertEquals(
            InSessionStopTracker.Result.Continue,
            tracker.onSample(speedMps = 0f, elapsedMs = 23_000L),
        )
        assertEquals(
            InSessionStopTracker.Result.Confirmed,
            tracker.onSample(speedMps = 0f, elapsedMs = 24_000L),
        )
    }

    @Test
    fun walkingDoesNotCountAsDrive() {
        val tracker = InSessionStopTracker()
        tracker.onSample(speedMps = 5f / 3.6f, elapsedMs = 0L)
        assertEquals(
            InSessionStopTracker.Result.Continue,
            tracker.onSample(speedMps = 0f, elapsedMs = 15_000L),
        )
    }

    @Test
    fun requiresDriveAgainAfterConfirm() {
        val tracker = InSessionStopTracker()
        tracker.onSample(speedMps = 20f / 3.6f, elapsedMs = 0L)
        tracker.onSample(speedMps = 0f, elapsedMs = 1_000L)
        assertEquals(
            InSessionStopTracker.Result.Confirmed,
            tracker.onSample(speedMps = 0f, elapsedMs = 11_000L),
        )
        // Still stopped — no second fire without driving again
        assertEquals(
            InSessionStopTracker.Result.Continue,
            tracker.onSample(speedMps = 0f, elapsedMs = 30_000L),
        )
    }
}
