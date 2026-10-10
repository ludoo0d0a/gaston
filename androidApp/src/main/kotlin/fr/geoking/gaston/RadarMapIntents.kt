package fr.geoking.gaston

/**
 * Phone notification tap → open the radar amenity map, optionally centered on a zone.
 */
object RadarMapIntents {
    const val ACTION_OPEN_RADAR_MAP = "fr.geoking.gaston.action.OPEN_RADAR_MAP"

    const val EXTRA_LATITUDE = "fr.geoking.gaston.extra.RADAR_LATITUDE"
    const val EXTRA_LONGITUDE = "fr.geoking.gaston.extra.RADAR_LONGITUDE"
}

/** Deep-link payload to open the speed-camera map (optional center). */
data class OpenRadarMapRequest(
    val latitude: Double? = null,
    val longitude: Double? = null,
)
