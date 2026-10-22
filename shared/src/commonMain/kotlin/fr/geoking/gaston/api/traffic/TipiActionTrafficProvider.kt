package fr.geoking.gaston.api.traffic

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * TIPI restricted Action B or Action C SituationPublication feed.
 * [enabled] only when the underlying client has credentials.
 *
 * National DATEX refresh is [NonCancellable] (same rationale as [BisonFuteTrafficProvider]).
 */
class TipiActionTrafficProvider(
    private val client: TipiRestrictedTrafficClient,
    private val providerId: String,
    private val trafficRepository: TrafficRepository? = null,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) : TrafficProvider {

    override val enabled: Boolean
        get() = client.hasCredentials

    override suspend fun getTraffic(request: TrafficRequest): TrafficInfo? {
        if (!enabled) return null
        val q = queryBbox(request) ?: return null

        val repo = trafficRepository
        val fetchedEvents = withContext(NonCancellable) {
            client.fetchAndStoreTrafficEvents()
        }

        val events = if (repo != null) {
            repo.getTrafficInBbox(
                latMin = q.latMin,
                lonMin = q.lonMin,
                latMax = q.latMax,
                lonMax = q.lonMax,
                maxEvents = FranceTrafficProvider.MAX_TRAFFIC_EVENTS,
                sourceId = client.sourceId,
            )
        } else {
            fetchedEvents.filter { e ->
                val b = e.bbox ?: return@filter false
                b.intersects(q)
            }.take(FranceTrafficProvider.MAX_TRAFFIC_EVENTS)
        }

        if (events.isEmpty()) return null
        return TrafficInfo(events = events, providerId = providerId)
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
