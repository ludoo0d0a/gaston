package fr.geoking.gaston.api.traffic

import fr.geoking.gaston.poi.PoiProviderType
import fr.geoking.gaston.poi.effectiveTrafficOverlayProviders
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Region + provider registration. [selectionKey] gates the provider via Settings → Sources traffic chips;
 * null means always eligible when the region matches (e.g. Luxembourg CITA).
 */
data class TrafficProviderRegistration(
    val region: GeographicRegion,
    val provider: TrafficProvider,
    val selectionKey: PoiProviderType? = null,
)

/**
 * Returns the appropriate [TrafficProvider]s for a given location.
 * Holds a list of registrations; adding a country is a single "add region + provider" step.
 */
class TrafficProviderFactory(
    private val registrations: List<TrafficProviderRegistration>
) {
    /**
     * Returns a provider that can supply traffic for the given coordinates, or null if none.
     * Prefer [getProviders] when user selection / multi-provider merge matters.
     */
    fun getProvider(latitude: Double, longitude: Double): TrafficProvider? =
        getProviders(latitude, longitude).firstOrNull()

    /**
     * All enabled providers covering [latitude]/[longitude], filtered by [selectedProviders] traffic chips.
     * Empty traffic selection → Tipi + TomTom (see [effectiveTrafficOverlayProviders]).
     */
    fun getProviders(
        latitude: Double,
        longitude: Double,
        selectedProviders: Set<PoiProviderType> = emptySet(),
    ): List<TrafficProvider> {
        val allowed = selectedProviders.effectiveTrafficOverlayProviders()
        val seen = mutableSetOf<TrafficProvider>()
        val out = mutableListOf<TrafficProvider>()
        for (reg in registrations) {
            if (!reg.provider.enabled) continue
            if (!reg.region.contains(latitude, longitude)) continue
            val key = reg.selectionKey
            if (key != null && key !in allowed) continue
            if (reg.provider in seen) continue
            seen.add(reg.provider)
            out.add(reg.provider)
        }
        return out
    }

    /**
     * Returns all providers that cover any part of the route (e.g. Luxembourg + France).
     */
    fun getProvidersForRoute(
        routePoints: List<Pair<Double, Double>>,
        selectedProviders: Set<PoiProviderType> = emptySet(),
    ): List<TrafficProvider> {
        if (routePoints.isEmpty()) return emptyList()
        val seen = mutableSetOf<TrafficProvider>()
        val out = mutableListOf<TrafficProvider>()
        for ((lat, lon) in routePoints) {
            for (provider in getProviders(lat, lon, selectedProviders)) {
                if (provider !in seen) {
                    seen.add(provider)
                    out.add(provider)
                }
            }
        }
        return out
    }

    companion object {
        /**
         * Fetch and merge traffic from several providers (parallel, per-provider fault tolerant).
         */
        suspend fun mergeTraffic(
            providers: List<TrafficProvider>,
            request: TrafficRequest,
            maxEvents: Int = FranceTrafficProvider.MAX_TRAFFIC_EVENTS,
        ): TrafficInfo? = coroutineScope {
            if (providers.isEmpty()) return@coroutineScope null
            if (providers.size == 1) {
                return@coroutineScope runCatching { providers[0].getTraffic(request) }.getOrNull()
            }
            val deferred = providers.map { p ->
                async { runCatching { p.getTraffic(request) }.getOrNull() }
            }
            val byId = linkedMapOf<String, TrafficEvent>()
            val noId = mutableListOf<TrafficEvent>()
            var lastProviderId: String? = null
            for (d in deferred) {
                val info = d.await() ?: continue
                lastProviderId = info.providerId
                for (e in info.events) {
                    val id = e.sourceId
                    if (id.isNullOrBlank()) {
                        noId.add(e)
                    } else {
                        byId[id] = e
                    }
                }
            }
            val events = (byId.values.toList() + noId).take(maxEvents)
            if (events.isEmpty()) return@coroutineScope null
            TrafficInfo(
                events = events,
                providerId = lastProviderId ?: "merged",
            )
        }
    }
}
