package fr.geoking.gaston.api.traffic

import fr.geoking.gaston.api.overpass.OverpassClient

/**
 * Enriches traffic events with OSM/Overpass road polygon geometry lazily when displayed on the map,
 * and caches the resulting polygon coordinates in [TrafficRepository].
 */
class TrafficPolygonEnricher(
    private val overpassClient: OverpassClient?,
    private val trafficRepository: TrafficRepository?
) {

    suspend fun enrichAndCachePolygon(event: TrafficEvent, sourceId: String = BisonFuteTrafficClient.SOURCE_ID): List<Pair<Double, Double>>? {
        if (event.zonePoints != null && event.zonePoints.size >= 2) {
            return event.zonePoints
        }

        val eventId = event.sourceId ?: return null
        val lat = event.poiLatitude ?: return null
        val lon = event.poiLongitude ?: return null
        val client = overpassClient ?: return null

        return try {
            val elements = client.queryHighwayWaysAround(lat, lon, radiusMeters = 50)
            val way = elements.firstOrNull() ?: return null
            val wayLat = way.lat
            val wayLon = way.lon

            val points = listOf(Pair(lat, lon), Pair(wayLat, wayLon))
            if (points.size >= 2) {
                trafficRepository?.updateZonePoints(
                    eventId = eventId,
                    sourceId = sourceId,
                    zonePoints = points
                )
                points
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
