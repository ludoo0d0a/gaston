package fr.geoking.gaston.auto

import android.location.Location
import kotlin.math.abs

/**
 * Resolves map bearing for heading-up mode from a [Location] fix.
 * Uses GPS course bearing when moving; otherwise keeps the last stable bearing.
 */
object AutoMapHeading {
    /** Minimum speed (m/s) before trusting [Location.getBearing]. */
    private const val MIN_SPEED_MPS = 1.0f

    /** Ignore bearing jitter below this delta (degrees). */
    private const val MIN_BEARING_DELTA_DEG = 3f

    fun resolveBearing(location: Location?, lastBearingDegrees: Float): Float {
        val loc = location ?: return normalizeDegrees(lastBearingDegrees)
        if (!loc.hasBearing()) return normalizeDegrees(lastBearingDegrees)
        if (loc.hasSpeed() && loc.speed < MIN_SPEED_MPS) {
            return normalizeDegrees(lastBearingDegrees)
        }
        val candidate = normalizeDegrees(loc.bearing)
        if (lastBearingDegrees == 0f && !loc.hasSpeed()) {
            return candidate
        }
        val delta = shortestAngleDelta(lastBearingDegrees, candidate)
        return if (abs(delta) < MIN_BEARING_DELTA_DEG) {
            normalizeDegrees(lastBearingDegrees)
        } else {
            candidate
        }
    }

    fun effectiveBearing(mode: MapOrientationMode, headingDegrees: Float): Float =
        when (mode) {
            MapOrientationMode.NorthUp -> 0f
            MapOrientationMode.HeadingUp -> normalizeDegrees(headingDegrees)
        }

    /**
     * Orientation after a manual bearing change (debug slider / simulated heading).
     *
     * Non-zero heading requires [MapOrientationMode.HeadingUp] so [effectiveBearing] applies.
     * Bearing 0° must **not** force [MapOrientationMode.NorthUp]: that moves
     * [AutoMapFollowFocalPoint] (look-ahead → center) and pans the map. In AA, GPS heading
     * can be ~0° while still heading-up; only an explicit orientation toggle switches mode.
     */
    fun modeAfterBearingChange(current: MapOrientationMode, bearingDegrees: Float): MapOrientationMode {
        val bearing = normalizeDegrees(bearingDegrees)
        return if (bearing != 0f && current == MapOrientationMode.NorthUp) {
            MapOrientationMode.HeadingUp
        } else {
            current
        }
    }

    fun normalizeDegrees(degrees: Float): Float {
        var d = degrees % 360f
        if (d < 0f) d += 360f
        return d
    }

    /** Signed shortest turn from [from] to [to], in [-180, 180]. */
    fun shortestAngleDelta(from: Float, to: Float): Float {
        var delta = normalizeDegrees(to) - normalizeDegrees(from)
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }
}
