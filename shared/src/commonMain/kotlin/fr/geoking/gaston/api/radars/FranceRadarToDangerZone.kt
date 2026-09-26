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
): DangerZone {
    return DangerZoneFactory.fromSpeedControlPoint(
        id = "fr_dz_$id",
        latitude = latitude,
        longitude = longitude,
        speedLimitKmH = vma,
        source = "FranceRadars",
        csvType = type,
        roadClassOverride = roadClassOverride,
    )
}
