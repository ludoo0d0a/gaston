package fr.geoking.gaston.api.traffic

/**
 * Request shape for [TrafficProvider.getTraffic].
 * Either a bounding box (e.g. visible map area) or route points (e.g. for route screen).
 * Provider can decide what to fetch (e.g. CITA fetches by road when bbox intersects Luxembourg).
 */
sealed class TrafficRequest {
    /**
     * Request traffic for a rectangular area (visible map viewport).
     * Order matches `&bounding_box=latMin,lonMin,latMax,lonMax`
     * (e.g. `47,-85,47.5,-84.5`). Providers may remap for their own query param
     * (TomTom uses `lonMin,latMin,lonMax,latMax`).
     */
    data class Bbox(
        val latMin: Double,
        val lonMin: Double,
        val latMax: Double,
        val lonMax: Double
    ) : TrafficRequest()

    /** Request traffic relevant to a route (provider may use bbox of points). */
    data class Route(
        val points: List<Pair<Double, Double>>
    ) : TrafficRequest()
}
