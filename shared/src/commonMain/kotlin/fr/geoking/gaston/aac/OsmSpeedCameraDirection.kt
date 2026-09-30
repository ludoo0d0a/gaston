package fr.geoking.gaston.aac

/**
 * Confidence that [monitoredBearingDegrees] reflects the **traffic direction being controlled**
 * (not merely camera lens orientation).
 */
enum class DirectionConfidence {
    /** No usable direction info — do not filter alerts by sense. */
    None,
    /**
     * Ambiguous tags (degrees / cardinals often mean lens facing).
     * Do **not** filter alerts on this alone.
     */
    Low,
    /**
     * Reliable: enforcement from→to, forward/backward along a way, or both.
     */
    High,
}

/**
 * Parsed OSM direction for a speed camera, for merge / alert filtering.
 */
data class OsmDirectionInfo(
    val confidence: DirectionConfidence,
    /** Bearing of monitored traffic [0, 360), null if bidirectional or unknown. */
    val monitoredBearingDegrees: Double? = null,
    val bidirectional: Boolean = false,
    val osmNodeId: Long? = null,
    val matchedDistanceMeters: Double? = null,
    /** Optional maxspeed from OSM when data.gouv VMA is missing. */
    val osmMaxspeedKmH: Int? = null,
) {
    companion object {
        val NONE = OsmDirectionInfo(confidence = DirectionConfidence.None)
    }
}

/**
 * Parses OSM `direction=*` and enforcement bearings into [OsmDirectionInfo].
 */
object OsmSpeedCameraDirection {
    const val MONITOR_TOLERANCE_DEGREES = 45.0

    fun parseMaxspeedKmH(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val digits = raw.filter { it.isDigit() }
        return digits.toIntOrNull()?.takeIf { it in 10..150 }
    }

    /**
     * @param directionTag raw `direction=*` on the camera node
     * @param wayBearingDegrees bearing of the parent highway (forward = way direction), if known
     * @param enforcementBearingDegrees bearing from enforcement `from` → `to`, if known
     */
    fun resolve(
        directionTag: String?,
        wayBearingDegrees: Double? = null,
        enforcementBearingDegrees: Double? = null,
        osmNodeId: Long? = null,
        matchedDistanceMeters: Double? = null,
        osmMaxspeedKmH: Int? = null,
    ): OsmDirectionInfo {
        if (enforcementBearingDegrees != null) {
            return OsmDirectionInfo(
                confidence = DirectionConfidence.High,
                monitoredBearingDegrees = normalizeBearing(enforcementBearingDegrees),
                bidirectional = false,
                osmNodeId = osmNodeId,
                matchedDistanceMeters = matchedDistanceMeters,
                osmMaxspeedKmH = osmMaxspeedKmH,
            )
        }

        val d = directionTag?.trim()?.lowercase().orEmpty()
        if (d.isEmpty()) {
            return OsmDirectionInfo(
                confidence = DirectionConfidence.None,
                osmNodeId = osmNodeId,
                matchedDistanceMeters = matchedDistanceMeters,
                osmMaxspeedKmH = osmMaxspeedKmH,
            )
        }

        when (d) {
            "both", "two_way", "twoway" -> return OsmDirectionInfo(
                confidence = DirectionConfidence.High,
                monitoredBearingDegrees = null,
                bidirectional = true,
                osmNodeId = osmNodeId,
                matchedDistanceMeters = matchedDistanceMeters,
                osmMaxspeedKmH = osmMaxspeedKmH,
            )
            "forward" -> if (wayBearingDegrees != null) {
                return OsmDirectionInfo(
                    confidence = DirectionConfidence.High,
                    monitoredBearingDegrees = normalizeBearing(wayBearingDegrees),
                    osmNodeId = osmNodeId,
                    matchedDistanceMeters = matchedDistanceMeters,
                    osmMaxspeedKmH = osmMaxspeedKmH,
                )
            }
            "backward" -> if (wayBearingDegrees != null) {
                return OsmDirectionInfo(
                    confidence = DirectionConfidence.High,
                    monitoredBearingDegrees = normalizeBearing(wayBearingDegrees + 180.0),
                    osmNodeId = osmNodeId,
                    matchedDistanceMeters = matchedDistanceMeters,
                    osmMaxspeedKmH = osmMaxspeedKmH,
                )
            }
        }

        parseCardinalOrDegrees(d)?.let { deg ->
            return OsmDirectionInfo(
                confidence = DirectionConfidence.Low,
                monitoredBearingDegrees = deg,
                osmNodeId = osmNodeId,
                matchedDistanceMeters = matchedDistanceMeters,
                osmMaxspeedKmH = osmMaxspeedKmH,
            )
        }

        return OsmDirectionInfo(
            confidence = DirectionConfidence.None,
            osmNodeId = osmNodeId,
            matchedDistanceMeters = matchedDistanceMeters,
            osmMaxspeedKmH = osmMaxspeedKmH,
        )
    }

    fun normalizeBearing(degrees: Double): Double {
        var b = degrees % 360.0
        if (b < 0) b += 360.0
        return b
    }

    /**
     * True when vehicle heading is compatible with monitored traffic direction.
     * Bidirectional or missing High bearing → always compatible.
     */
    fun isVehicleCompatible(
        vehBearing: Double?,
        info: OsmDirectionInfo,
        toleranceDegrees: Double = MONITOR_TOLERANCE_DEGREES,
    ): Boolean {
        if (info.confidence != DirectionConfidence.High) return true
        if (info.bidirectional) return true
        val monitored = info.monitoredBearingDegrees ?: return true
        if (vehBearing == null) return true
        return DangerZoneEvaluator.angleDifference(vehBearing, monitored) <= toleranceDegrees
    }

    private fun parseCardinalOrDegrees(d: String): Double? {
        d.toDoubleOrNull()?.let { return normalizeBearing(it) }
        return when (d) {
            "n", "north" -> 0.0
            "ne", "northeast" -> 45.0
            "e", "east" -> 90.0
            "se", "southeast" -> 135.0
            "s", "south" -> 180.0
            "sw", "southwest" -> 225.0
            "w", "west" -> 270.0
            "nw", "northwest" -> 315.0
            else -> null
        }
    }
}
