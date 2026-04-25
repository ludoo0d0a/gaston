package fr.geoking.gaston.api.traffic

/**
 * Geographic region for which a [TrafficProvider] supplies data.
 * Used by [TrafficProviderFactory] to select the right provider for (lat, lon).
 */
sealed class GeographicRegion {

    abstract fun contains(latitude: Double, longitude: Double): Boolean

    /** Bounding box: lat in [latMin, latMax], lon in [lonMin, lonMax]. */
    data class Bbox(
        val latMin: Double,
        val lonMin: Double,
        val latMax: Double,
        val lonMax: Double
    ) : GeographicRegion() {
        override fun contains(latitude: Double, longitude: Double): Boolean =
            latitude in latMin..latMax && longitude in lonMin..lonMax
    }

    /**
     * Matches any coordinates. Register last in [TrafficProviderFactory] so regional providers
     * (e.g. CITA) win inside their bbox.
     */
    data object Everywhere : GeographicRegion() {
        override fun contains(latitude: Double, longitude: Double): Boolean = true
    }
}
