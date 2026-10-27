package fr.geoking.gaston.api.traffic

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * [TrafficProvider] for mainland France + Corsica using the open Bison Futé / TIPI DATEX feed
 * (national non-concessioned network).
 *
 * National DATEX dumps are multi‑MB: refresh runs under [NonCancellable] so map camera moves
 * do not abort store-into-Room; viewport only affects the subsequent bbox query.
 */
class BisonFuteTrafficProvider(
    private val client: BisonFuteTrafficClient,
    private val trafficRepository: TrafficRepository? = null,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) : TrafficProvider {

    private val franceBbox = GeographicRegion.Bbox(
        latMin = FRANCE_LAT_MIN,
        lonMin = FRANCE_LON_MIN,
        latMax = FRANCE_LAT_MAX,
        lonMax = FRANCE_LON_MAX
    )

    override val enabled: Boolean = true

    override suspend fun getTraffic(request: TrafficRequest): TrafficInfo? {
        val q = queryBbox(request) ?: return null
        val midLat = (q.latMin + q.latMax) / 2
        val midLon = (q.lonMin + q.lonMax) / 2
        if (!franceBbox.contains(midLat, midLon)) return null

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
                maxEvents = MAX_TRAFFIC_EVENTS,
                sourceId = BisonFuteTrafficClient.SOURCE_ID,
            )
        } else {
            fetchedEvents.filter { e ->
                val b = e.bbox ?: return@filter false
                b.intersects(q)
            }.take(MAX_TRAFFIC_EVENTS)
        }

        if (events.isEmpty()) return null
        return TrafficInfo(events = events, providerId = PROVIDER_ID)
    }

    companion object {
        const val PROVIDER_ID = "bison_fute"
        const val MAX_TRAFFIC_EVENTS = 250
        const val FRANCE_LAT_MIN = 41.2
        const val FRANCE_LON_MIN = -5.5
        const val FRANCE_LAT_MAX = 51.2
        const val FRANCE_LON_MAX = 9.7
    }
}
