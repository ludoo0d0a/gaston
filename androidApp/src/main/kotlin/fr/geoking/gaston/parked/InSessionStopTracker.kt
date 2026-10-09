package fr.geoking.gaston.parked

/**
 * During an Android Auto session: require vehicle-like speed at least once, then
 * confirm a stop after [requiredStationaryMs] without vehicle-like speed.
 * Walking / missing speed do not reset [hasDriven], but vehicle speed resets the
 * stationary window. After [Result.Confirmed], driving is required again before
 * another confirmation.
 */
class InSessionStopTracker(
    private val requiredStationaryMs: Long = REQUIRED_MS,
    private val driveSpeedMps: Float = DRIVE_SPEED_MPS,
) {
    enum class Result {
        Continue,
        Confirmed,
    }

    private var hasDriven: Boolean = false
    private var stationarySinceElapsedMs: Long? = null

    fun onSample(speedMps: Float?, elapsedMs: Long): Result {
        val vehicleMoving = speedMps != null && speedMps >= driveSpeedMps
        if (vehicleMoving) {
            hasDriven = true
            stationarySinceElapsedMs = null
            return Result.Continue
        }
        if (!hasDriven) return Result.Continue

        val since = stationarySinceElapsedMs ?: elapsedMs.also { stationarySinceElapsedMs = it }
        if (elapsedMs - since >= requiredStationaryMs) {
            hasDriven = false
            stationarySinceElapsedMs = null
            return Result.Confirmed
        }
        return Result.Continue
    }

    fun reset() {
        hasDriven = false
        stationarySinceElapsedMs = null
    }

    companion object {
        /** 15 km/h in m/s — same threshold as [PostDestroyDriveAbortTracker]. */
        const val DRIVE_SPEED_MPS: Float = 15f / 3.6f
        const val REQUIRED_MS: Long = 10_000L
    }
}
