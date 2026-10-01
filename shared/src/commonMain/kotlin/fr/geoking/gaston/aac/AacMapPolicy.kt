package fr.geoking.gaston.aac

/**
 * Map / alert presentation policy for AAC Level A (France).
 *
 * Alerts must use extended [DangerZone]s (never “radar à X m”).
 * Map amenity display uses Overpass `highway=speed_camera` when Lufop has no API key
 * (fallback), or Lufop / Luxembourg official dumps when configured.
 */
object AacMapPolicy {
    fun isFrance(countryCode: String?): Boolean {
        val cc = countryCode?.uppercase()?.trim()
        return cc == "FR" || cc == "FRA"
    }

    /** Rough mainland + Corsica bbox when country code is unavailable. */
    fun isLikelyFrance(latitude: Double, longitude: Double): Boolean {
        return latitude in 41.0..51.5 && longitude in -5.5..10.0
    }
}
