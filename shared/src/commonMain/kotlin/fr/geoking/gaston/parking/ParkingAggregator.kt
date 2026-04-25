package fr.geoking.gaston.parking

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Aggregates parking POIs from multiple [ParkingProvider]s. Uses [ParkingApiSelector] to call
 * only providers relevant to [ParkingQueryContext]. Merges and deduplicates by (id, providerId).
 */
class ParkingAggregator(
    private val providers: List<ParkingProvider>,
    private val selector: ParkingApiSelector
) {
    /**
     * Parking POIs near the context points (user and/or from/to). Calls only providers that
     * cover at least one of those points; merges and deduplicates by (id, providerId).
     */
    suspend fun getParkingNearby(context: ParkingQueryContext, radiusMeters: Int): List<ParkingPoi> =
        coroutineScope {
            val selected = selector.select(context)
            if (selected.isEmpty()) return@coroutineScope emptyList()
            val points = context.points()
            if (points.isEmpty()) return@coroutineScope emptyList()
            val deferred = selected.map { provider ->
                points.map { (lat, lon) ->
                    async {
                        if (!provider.covers(lat, lon)) return@async emptyList<ParkingPoi>()
                        runCatching {
                            provider.getParkingNearby(lat, lon, radiusMeters)
                                .map { it.copy(providerId = provider.id) }
                        }.getOrElse { emptyList() }
                    }
                }
            }.flatten()
            val results = deferred.awaitAll().flatten()
            deduplicate(results)
        }

    private fun deduplicate(pois: List<ParkingPoi>): List<ParkingPoi> {
        val seen = mutableSetOf<Pair<String, String>>()
        return pois.filter { poi ->
            val key = poi.id to (poi.providerId ?: "")
            seen.add(key)
        }
    }
}
