package fr.geoking.gaston.api.traffic

/**
 * [TrafficProvider] for mainland France + Corsica using the open Bison Futé / TIPI DATEX feed
 * (national non-concessioned network). No API key.
 */
class BisonFuteTrafficProvider(
    private val client: BisonFuteTrafficClient,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) : TrafficProvider {

    /** Approximate France + Corsica bounding box. */
    private val franceBbox = GeographicRegion.Bbox(
        latMin = FRANCE_LAT_MIN,
        lonMin = FRANCE_LON_MIN,
        latMax = FRANCE_LAT_MAX,
        lonMax = FRANCE_LON_MAX
    )

    override val enabled: Boolean = true

    override suspend fun getTraffic(request: TrafficRequest): TrafficInfo? {
        when (request) {
            is TrafficRequest.Bbox -> {
                val midLat = (request.latMin + request.latMax) / 2
                val midLon = (request.lonMin + request.lonMax) / 2
                if (!franceBbox.contains(midLat, midLon)) return null
            }
            is TrafficRequest.Route -> {
                if (request.points.isEmpty()) return null
                val (lat, lon) = request.points[request.points.size / 2]
                if (!franceBbox.contains(lat, lon)) return null
            }
        }
        val body = client.fetchDatexXml() ?: return null
        val all = BisonFuteDatexParser.parse(body, nowMs = nowMs())
        if (all.isEmpty()) return null
        val q = queryBbox(request) ?: return null
        val filtered = all.filter { e ->
            val b = e.bbox ?: return@filter false
            b.intersects(q)
        }
        if (filtered.isEmpty()) return null
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
        const val PROVIDER_ID = "bison_fute"
        const val FRANCE_LAT_MIN = 41.2
        const val FRANCE_LON_MIN = -5.5
        const val FRANCE_LAT_MAX = 51.2
        const val FRANCE_LON_MAX = 9.7
    }
}
