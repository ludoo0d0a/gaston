package fr.geoking.gaston.api.traffic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Composite France traffic: open Bison Futé always, plus TIPI Action B/C when enabled.
 * Merges events by [TrafficEvent.sourceId] (later feeds overwrite: open → B → C).
 *
 * Runs sub-providers in parallel and catches exceptions per provider so an error in
 * Action B or C (e.g. HTTP 401/403/500) will not fail the request or drop open traffic.
 */
class FranceTrafficProvider(
    private val openProvider: TrafficProvider,
    private val actionB: TrafficProvider? = null,
    private val actionC: TrafficProvider? = null
) : TrafficProvider {

    private val franceBbox = GeographicRegion.Bbox(
        latMin = BisonFuteTrafficProvider.FRANCE_LAT_MIN,
        lonMin = BisonFuteTrafficProvider.FRANCE_LON_MIN,
        latMax = BisonFuteTrafficProvider.FRANCE_LAT_MAX,
        lonMax = BisonFuteTrafficProvider.FRANCE_LON_MAX
    )

    override val enabled: Boolean = true

    override suspend fun getTraffic(request: TrafficRequest): TrafficInfo? = coroutineScope {
        when (request) {
            is TrafficRequest.Bbox -> {
                val midLat = (request.latMin + request.latMax) / 2
                val midLon = (request.lonMin + request.lonMax) / 2
                if (!franceBbox.contains(midLat, midLon)) return@coroutineScope null
            }
            is TrafficRequest.Route -> {
                if (request.points.isEmpty()) return@coroutineScope null
                val (lat, lon) = request.points[request.points.size / 2]
                if (!franceBbox.contains(lat, lon)) return@coroutineScope null
            }
        }

        val openDeferred = async { trafficOrNull(openProvider, request) }
        val actionBDeferred = if (actionB?.enabled == true) {
            async { trafficOrNull(actionB, request) }
        } else null
        val actionCDeferred = if (actionC?.enabled == true) {
            async { trafficOrNull(actionC, request) }
        } else null

        val openInfo = openDeferred.await()
        val actionBInfo = actionBDeferred?.await()
        val actionCInfo = actionCDeferred?.await()

        val byId = linkedMapOf<String, TrafficEvent>()
        val noId = mutableListOf<TrafficEvent>()

        fun absorb(info: TrafficInfo?) {
            if (info == null) return
            for (e in info.events) {
                val id = e.sourceId
                if (id.isNullOrBlank()) {
                    noId.add(e)
                } else {
                    val existing = byId[id]
                    if (existing == null) {
                        byId[id] = e
                    } else {
                        // Merge or replace: if new event has coordinates (or existing lacks coordinates), update
                        val newHasCoords = e.poiLatitude != null && e.poiLongitude != null
                        val existingHasCoords = existing.poiLatitude != null && existing.poiLongitude != null
                        if (newHasCoords || !existingHasCoords) {
                            byId[id] = e
                        }
                    }
                }
            }
        }

        absorb(openInfo)
        absorb(actionBInfo)
        absorb(actionCInfo)

        val events = (byId.values.toList() + noId).take(MAX_TRAFFIC_EVENTS)
        if (events.isEmpty()) return@coroutineScope null
        TrafficInfo(events = events, providerId = PROVIDER_ID)
    }

    companion object {
        const val PROVIDER_ID = "france_traffic"
        const val MAX_TRAFFIC_EVENTS = 250

        private suspend fun trafficOrNull(
            provider: TrafficProvider,
            request: TrafficRequest,
        ): TrafficInfo? = try {
            provider.getTraffic(request)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}
