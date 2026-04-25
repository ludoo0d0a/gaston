package fr.geoking.gaston.transit

/**
 * Context for transit queries: user location and/or itinerary from/to.
 * Used by [TransitApiSelector] to decide which providers to call.
 */
data class TransitQueryContext(
    val userLat: Double? = null,
    val userLon: Double? = null,
    val fromLat: Double? = null,
    val fromLon: Double? = null,
    val toLat: Double? = null,
    val toLon: Double? = null
) {
    /** All (lat, lon) pairs that are present. */
    fun points(): List<Pair<Double, Double>> = buildList {
        if (userLat != null && userLon != null) add(userLat to userLon)
        if (fromLat != null && fromLon != null) add(fromLat to fromLon)
        if (toLat != null && toLon != null) add(toLat to toLon)
    }
}
