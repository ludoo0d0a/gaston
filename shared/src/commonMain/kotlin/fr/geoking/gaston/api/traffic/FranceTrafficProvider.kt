package fr.geoking.gaston.api.traffic

/**
 * Composite France traffic: open Bison Futé always, plus TIPI Action B/C when enabled.
 * Merges events by [TrafficEvent.sourceId] (later feeds overwrite: open → B → C).
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

        val byId = linkedMapOf<String, TrafficEvent>()
        val noId = mutableListOf<TrafficEvent>()

        fun absorb(info: TrafficInfo?) {
            if (info == null) return
            for (e in info.events) {
                val id = e.sourceId
                if (id.isNullOrBlank()) noId.add(e) else byId[id] = e
            }
        }

        absorb(openProvider.getTraffic(request))
        if (actionB?.enabled == true) absorb(actionB.getTraffic(request))
        if (actionC?.enabled == true) absorb(actionC.getTraffic(request))

        val events = byId.values.toList() + noId
        if (events.isEmpty()) return null
        return TrafficInfo(events = events, providerId = PROVIDER_ID)
    }

    companion object {
        const val PROVIDER_ID = "france_traffic"
    }
}
