package fr.geoking.gaston.parked

import org.junit.Assert.assertEquals
import org.junit.Test

class PostDestroyDriveAbortTrackerTest {

    @Test
    fun confirmsAfter30sWithoutVehicleSpeed() {
        val tracker = PostDestroyDriveAbortTracker(startElapsedMs = 1_000L)
        assertEquals(
            PostDestroyDriveAbortTracker.Result.Continue,
            tracker.onSample(speedMps = 0f, elapsedMs = 1_000L + 10_000L),
        )
        assertEquals(
            PostDestroyDriveAbortTracker.Result.Continue,
            tracker.onSample(speedMps = 5f / 3.6f, elapsedMs = 1_000L + 20_000L), // walking
        )
        assertEquals(
            PostDestroyDriveAbortTracker.Result.Confirmed,
            tracker.onSample(speedMps = null, elapsedMs = 1_000L + 30_000L),
        )
    }

    @Test
    fun abortsWhenVehicleSpeedDetected() {
        val tracker = PostDestroyDriveAbortTracker(startElapsedMs = 0L)
        assertEquals(
            PostDestroyDriveAbortTracker.Result.Aborted,
            tracker.onSample(speedMps = 15f / 3.6f, elapsedMs = 5_000L),
        )
    }

    @Test
    fun walkingSpeedDoesNotAbort() {
        val tracker = PostDestroyDriveAbortTracker(startElapsedMs = 0L)
        assertEquals(
            PostDestroyDriveAbortTracker.Result.Continue,
            tracker.onSample(speedMps = 5f / 3.6f, elapsedMs = 10_000L),
        )
    }

    @Test
    fun confirmsWhenElapsedPastRequiredEvenNearTimeout() {
        val tracker = PostDestroyDriveAbortTracker(startElapsedMs = 0L)
        assertEquals(
            PostDestroyDriveAbortTracker.Result.Confirmed,
            tracker.onSample(speedMps = null, elapsedMs = 40_000L),
        )
    }
}
