package fr.geoking.gaston.aac

/**
 * Map / alert presentation policy for AAC Level A (France).
 */
object AacMapPolicy {
    /**
     * Exact speed-control pins must not be shown on the map in France for alert UX.
     * Prefer extended danger zones (not drawn as control markers).
     */
    fun shouldShowExactControlPin(countryCode: String?): Boolean {
        val cc = countryCode?.uppercase()?.trim()
        return cc != null && cc != "FR" && cc != "FRA"
    }

    fun isFrance(countryCode: String?): Boolean {
        val cc = countryCode?.uppercase()?.trim()
        return cc == "FR" || cc == "FRA"
    }

    /** Rough mainland + Corsica bbox when country code is unavailable. */
    fun isLikelyFrance(latitude: Double, longitude: Double): Boolean {
        return latitude in 41.0..51.5 && longitude in -5.5..10.0
    }
}
