package fr.geoking.gaston.transit

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Aggregates results from multiple [TransitProvider]s. Uses [TransitApiSelector] to call
 * only providers relevant to [TransitQueryContext] (e.g. no Luxembourg API when user is in France).
 */
class TransitAggregator(
    private val providers: List<TransitProvider>,
    private val selector: TransitApiSelector
) {
    /**
     * Stops near the context points (user and/or from/to). Calls only providers whose region
     * contains at least one of those points; merges and deduplicates by (id, providerId).
     */
    suspend fun getStopsNearby(context: TransitQueryContext, radiusMeters: Int): List<TransitStop> =
        coroutineScope {
            val selected = selector.select(context)
            if (selected.isEmpty()) return@coroutineScope emptyList()
            val points = context.points()
            if (points.isEmpty()) return@coroutineScope emptyList()
            val deferred = selected.map { provider ->
                points.map { (lat, lon) ->
                    async {
                        if (!provider.covers(lat, lon)) return@async emptyList<TransitStop>()
                        runCatching {
                            provider.getStopsNearby(lat, lon, radiusMeters)
                                .map { it.copy(providerId = provider.id) }
                        }.getOrElse { emptyList() }
                    }
                }
            }.flatten()
            val results = deferred.awaitAll().flatten()
            deduplicateStops(results)
        }

    /**
     * Departures at [stopId]. Selector is used with a single point: if the stop is from a known
     * provider (e.g. id contains provider hint) we could restrict; for simplicity we ask all
     * selected providers and merge. Caller can pass context with user/from/to to limit which
     * providers are queried.
     */
    suspend fun getDepartures(context: TransitQueryContext, stopId: String): List<TransitDeparture> =
        coroutineScope {
            val selected = selector.select(context)
            if (selected.isEmpty()) return@coroutineScope emptyList()
            val deferred = selected.map { provider ->
                async {
                    runCatching {
                        provider.getDepartures(stopId).map { it.copy(providerId = provider.id) }
                    }.getOrElse { emptyList() }
                }
            }
            deferred.awaitAll().flatten()
        }

    private fun deduplicateStops(stops: List<TransitStop>): List<TransitStop> {
        val seen = mutableSetOf<Pair<String, String>>()
        return stops.filter { stop ->
            val key = stop.id to (stop.providerId ?: "")
            seen.add(key)
        }
    }
}
