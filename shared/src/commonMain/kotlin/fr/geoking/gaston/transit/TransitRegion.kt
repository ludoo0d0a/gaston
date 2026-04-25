package fr.geoking.gaston.transit

/**
 * Geographic region for transit APIs. Used by [TransitApiSelector] to choose which
 * providers to call based on user location and itinerary from/to.
 * Bounding boxes are conservative (mainland); no reverse geocoding.
 */
enum class TransitRegion(
    val latMin: Double,
    val latMax: Double,
    val lonMin: Double,
    val lonMax: Double
) {
    France(
        latMin = 41.33,
        latMax = 51.09,
        lonMin = -5.14,
        lonMax = 9.56
    ),
    Belgium(
        latMin = 49.50,
        latMax = 51.51,
        lonMin = 2.54,
        lonMax = 6.41
    ),
    Luxembourg(
        latMin = 49.44,
        latMax = 50.18,
        lonMin = 5.73,
        lonMax = 6.53
    );

    /** True if the point (WGS84) lies inside this region's bounding box. */
    fun contains(lat: Double, lon: Double): Boolean =
        lat in latMin..latMax && lon in lonMin..lonMax

    companion object {
        /** Returns the region containing (lat, lon), or null if none. Checks smaller regions first so e.g. Brussels matches Belgium, not France. */
        fun containing(lat: Double, lon: Double): TransitRegion? =
            listOf(Luxembourg, Belgium, France).firstOrNull { it.contains(lat, lon) }
    }
}
