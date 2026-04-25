package fr.geoking.gaston.api.traffic

/**
 * [TrafficProvider] for Luxembourg motorways using CITA public GeoJSON (service level per segment).
 * Replaces per-road DATEX polling with a single feed. Data © CITA.
 */
class CitaTrafficProvider(
    private val client: CitaGeoJsonTrafficClient
) : TrafficProvider {

    /** Luxembourg bounding box (approximate). */
    private val luxBbox = GeographicRegion.Bbox(
        latMin = 49.4,
        lonMin = 5.7,
        latMax = 50.2,
        lonMax = 6.6
    )

    override val enabled: Boolean = false

    override suspend fun getTraffic(request: TrafficRequest): TrafficInfo? {
        when (request) {
            is TrafficRequest.Bbox ->
                if (!luxBbox.contains((request.latMin + request.latMax) / 2, (request.lonMin + request.lonMax) / 2)) return null
            is TrafficRequest.Route ->
                if (request.points.isEmpty()) return null
                else {
                    val (lat, lon) = request.points[request.points.size / 2]
                    if (!luxBbox.contains(lat, lon)) return null
                }
        }
        val body = client.fetchGeoJson() ?: return null
        val all = CitaGeoJsonParser.parse(body)
        if (all.isEmpty()) return null
        val q = queryBbox(request) ?: return null
        val filtered = all.filter { e ->
            val b = e.bbox ?: return@filter false
            b.intersects(q)
        }
        return TrafficInfo(events = filtered, providerId = PROVIDER_ID)
    }

    private fun queryBbox(request: TrafficRequest): Bbox? = when (request) {
        is TrafficRequest.Bbox -> Bbox(
            latMin = request.latMin.coerceAtMost(request.latMax),
            lonMin = request.lonMin.coerceAtMost(request.lonMax),
            latMax = request.latMax.coerceAtLeast(request.latMin),
            lonMax = request.lonMax.coerceAtLeast(request.lonMin)
        )
        is TrafficRequest.Route -> {
            val pts = request.points
            if (pts.isEmpty()) return null
            val lats = pts.map { it.first }
            val lons = pts.map { it.second }
            Bbox(
                latMin = lats.minOrNull()!!,
                lonMin = lons.minOrNull()!!,
                latMax = lats.maxOrNull()!!,
                lonMax = lons.maxOrNull()!!
            )
        }
    }

    private fun Bbox.intersects(o: Bbox): Boolean =
        latMin <= o.latMax && latMax >= o.latMin && lonMin <= o.lonMax && lonMax >= o.lonMin

    companion object {
        const val PROVIDER_ID = "cita"
    }
}
