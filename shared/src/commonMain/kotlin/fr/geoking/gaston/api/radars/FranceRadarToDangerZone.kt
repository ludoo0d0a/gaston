package fr.geoking.gaston.api.radars

import fr.geoking.gaston.aac.DangerZone
import fr.geoking.gaston.aac.DangerZoneFactory
import fr.geoking.gaston.aac.RoadNetworkClass

/**
 * Converts France open-data fixed radar records into extended AAC [DangerZone]s.
 * Does not expose control points for alert UX — use zones only.
 */
fun FranceRadarRecord.toDangerZone(
    roadClassOverride: RoadNetworkClass? = null,
    source: String? = null,
): DangerZone {
    val resolvedSource = source ?: when {
        id.startsWith("lu_") -> "LuxembourgRadars"
        else -> "FranceRadars"
    }
    val zoneIdPrefix = when (resolvedSource) {
        "LuxembourgRadars" -> "lu_dz"
        else -> "fr_dz"
    }
    return DangerZoneFactory.fromSpeedControlPoint(
        id = "${zoneIdPrefix}_$id",
        latitude = latitude,
        longitude = longitude,
        speedLimitKmH = vma,
        source = resolvedSource,
        csvType = type,
        roadClassOverride = roadClassOverride,
    )
}
