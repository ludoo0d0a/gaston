package fr.geoking.gaston.api.routing

/**
 * Result of a route request: geometry as list of (latitude, longitude) points and total distance.
 */
data class RouteResult(
    /** Points along the route in order: (lat, lon). */
    val points: List<Pair<Double, Double>>,
    /** Total distance in meters. */
    val distanceMeters: Double
)
