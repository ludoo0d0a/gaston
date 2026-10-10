package fr.geoking.gaston.api.traffic

/**
 * TIPI restricted Action B or Action C SituationPublication feed.
 * [enabled] only when the underlying client has credentials.
 */
class TipiActionTrafficProvider(
    private val client: TipiRestrictedTrafficClient,
    private val providerId: String,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) : TrafficProvider {

    override val enabled: Boolean
        get() = client.hasCredentials

    override suspend fun getTraffic(request: TrafficRequest): TrafficInfo? {
        if (!enabled) return null
        val body = client.fetchDatexXml() ?: return null
        val all = DatexSituationParser.parse(body, nowMs = nowMs(), defaultRoadRef = "FR")
        if (all.isEmpty()) return null
        val q = queryBbox(request) ?: return null
        val filtered = all.filter { e ->
            val b = e.bbox ?: return@filter false
            b.intersects(q)
        }
        if (filtered.isEmpty()) return null
        return TrafficInfo(events = filtered, providerId = providerId)
    }

    companion object {
        const val ACTION_C_ID = "tipi_action_c"
        const val ACTION_B_ID = "tipi_action_b"
    }
}

internal fun queryBbox(request: TrafficRequest): Bbox? = when (request) {
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

internal fun Bbox.intersects(o: Bbox): Boolean =
    latMin <= o.latMax && latMax >= o.latMin && lonMin <= o.lonMax && lonMax >= o.lonMin
