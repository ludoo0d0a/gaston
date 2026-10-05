package fr.geoking.gaston.parked

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WalkAwayDistanceTrackerTest {

    @Test
    fun continuesWhenWithinThreshold() {
        val tracker = WalkAwayDistanceTracker(48.8566, 2.3522)
        // ~20 m north
        val result = tracker.onSample(48.85678, 2.3522)
        assertEquals(WalkAwayDistanceTracker.Result.Continue, result)
    }

    @Test
    fun triggersWhenBeyond50m() {
        val tracker = WalkAwayDistanceTracker(48.8566, 2.3522)
        // ~111 m north (0.001 deg lat ≈ 111 m)
        val result = tracker.onSample(48.8576, 2.3522)
        assertEquals(WalkAwayDistanceTracker.Result.Triggered, result)
    }
}

class ParkCandidateSaveHelperTest {

    @Test
    fun prefersMatchingCandidateCoords() {
        val candidate = ParkCandidate(
            vehicleId = "v1",
            latitude = 48.0,
            longitude = 2.0,
            createdAtEpochMs = 1L,
        )
        val coords = ParkCandidateSaveHelper.resolveSaveCoords(
            candidate = candidate,
            vehicleId = "v1",
            fallbackLat = 49.0,
            fallbackLon = 3.0,
        )
        assertEquals(48.0 to 2.0, coords)
    }

    @Test
    fun fallsBackWhenVehicleMismatch() {
        val candidate = ParkCandidate(
            vehicleId = "v1",
            latitude = 48.0,
            longitude = 2.0,
            createdAtEpochMs = 1L,
        )
        val coords = ParkCandidateSaveHelper.resolveSaveCoords(
            candidate = candidate,
            vehicleId = "v2",
            fallbackLat = 49.0,
            fallbackLon = 3.0,
        )
        assertEquals(49.0 to 3.0, coords)
    }

    @Test
    fun nullWhenNoCandidateAndNoFallback() {
        assertNull(
            ParkCandidateSaveHelper.resolveSaveCoords(
                candidate = null,
                vehicleId = "v1",
                fallbackLat = null,
                fallbackLon = 2.0,
            )
        )
    }
}
