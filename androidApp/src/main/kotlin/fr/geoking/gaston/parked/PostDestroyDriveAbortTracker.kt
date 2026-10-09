package fr.geoking.gaston.parked

/**
 * After AA session destroy: wait [requiredStationaryMs] wall-clock unless
 * phone GPS reports vehicle-like speed (still driving). Walking / missing speed do not abort.
 */
class PostDestroyDriveAbortTracker(
    private val startElapsedMs: Long,
    private val requiredStationaryMs: Long = REQUIRED_MS,
    private val driveAbortSpeedMps: Float = DRIVE_ABORT_SPEED_MPS,
    private val timeoutMs: Long = TIMEOUT_MS,
) {
    enum class Result {
        Continue,
        Confirmed,
        Aborted,
    }

    fun onSample(speedMps: Float?, elapsedMs: Long): Result {
        if (speedMps != null && speedMps >= driveAbortSpeedMps) {
            return Result.Aborted
        }
        val elapsed = elapsedMs - startElapsedMs
        if (elapsed >= requiredStationaryMs) {
            return Result.Confirmed
        }
        if (elapsed >= timeoutMs) {
            return Result.Aborted
        }
        return Result.Continue
    }

    companion object {
        /** 15 km/h in m/s. */
        const val DRIVE_ABORT_SPEED_MPS: Float = 15f / 3.6f
        const val REQUIRED_MS: Long = 10_000L
        const val TIMEOUT_MS: Long = 20_000L
    }
}
