package fr.geoking.gaston.aac

import fr.geoking.gaston.api.overpass.OverpassClient
import fr.geoking.gaston.api.overpass.OverpassElement
import fr.geoking.gaston.shared.location.haversineKm
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class RoadContextSource {
    Osm,
    Vma,
    Thoroughfare,
    Unknown,
}

data class RoadContext(
    val roadClass: RoadNetworkClass?,
    val isOnMotorway: Boolean,
    val source: RoadContextSource,
) {
    companion object {
        val Unknown = RoadContext(
            roadClass = null,
            isOnMotorway = false,
            source = RoadContextSource.Unknown,
        )
    }
}

/**
 * Classifies the road network near a GPS point via Overpass highway ways,
 * with VMA and thoroughfare-name fallbacks.
 */
class OsmRoadClassifier(
    private val overpassClient: OverpassClient,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS,
    private val cacheReuseKm: Double = DEFAULT_CACHE_REUSE_KM,
) {
    private val mutex = Mutex()
    private var lastCache: CachedResult? = null
    private val cellCache = mutableMapOf<Long, CachedResult>()

    /**
     * @param speedLimitKmH optional posted limit (VMA) used when OSM fails
     * @param thoroughfare optional reverse-geocoded road name used when OSM and VMA fail
     */
    suspend fun classify(
        latitude: Double,
        longitude: Double,
        speedLimitKmH: Int? = null,
        thoroughfare: String? = null,
    ): RoadContext {
        val cached = mutex.withLock { lookupCache(latitude, longitude, nowMs()) }
        if (cached != null) return cached

        val osmContext = try {
            val ways = overpassClient.queryHighwayWaysAround(latitude, longitude)
            classifyFromWays(ways)
        } catch (_: Exception) {
            null
        }

        val context = osmContext
            ?: fallbackFromVma(speedLimitKmH)
            ?: fallbackFromThoroughfare(thoroughfare)
            ?: RoadContext.Unknown

        mutex.withLock {
            // Another coroutine may have filled the cache while we fetched.
            val raced = lookupCache(latitude, longitude, nowMs())
            if (raced != null) return@withLock raced
            storeCache(latitude, longitude, context, nowMs())
            context
        }.let { return it }
    }

    private fun lookupCache(latitude: Double, longitude: Double, now: Long): RoadContext? {
        pruneCellCache(now)
        val last = lastCache
        if (last != null &&
            now - last.atMs < cacheTtlMs &&
            haversineKm(last.lat, last.lon, latitude, longitude) < cacheReuseKm
        ) {
            return last.context
        }
        val cellHit = cellCache[cellKey(latitude, longitude)]
        if (cellHit != null && now - cellHit.atMs < cacheTtlMs) {
            lastCache = cellHit.copy(lat = latitude, lon = longitude)
            return cellHit.context
        }
        return null
    }

    private fun storeCache(latitude: Double, longitude: Double, context: RoadContext, now: Long) {
        val cached = CachedResult(latitude, longitude, now, context)
        lastCache = cached
        cellCache[cellKey(latitude, longitude)] = cached
    }

    private fun classifyFromWays(ways: List<OverpassElement>): RoadContext? {
        if (ways.isEmpty()) return null
        val best = ways.maxByOrNull { DangerZoneDistances.osmHighwayRank(it.highway()) } ?: return null
        val roadClass = DangerZoneDistances.fromOsmHighway(best.highway()) ?: return null
        return RoadContext(
            roadClass = roadClass,
            isOnMotorway = roadClass == RoadNetworkClass.Motorway,
            source = RoadContextSource.Osm,
        )
    }

    private fun fallbackFromVma(speedLimitKmH: Int?): RoadContext? {
        if (speedLimitKmH == null) return null
        val roadClass = DangerZoneDistances.classifyFromSpeedLimitKmH(speedLimitKmH)
        return RoadContext(
            roadClass = roadClass,
            isOnMotorway = roadClass == RoadNetworkClass.Motorway,
            source = RoadContextSource.Vma,
        )
    }

    private fun fallbackFromThoroughfare(thoroughfare: String?): RoadContext? {
        if (!ThoroughfareHighwayHeuristic.isLikelyHighway(thoroughfare)) return null
        return RoadContext(
            roadClass = RoadNetworkClass.Motorway,
            isOnMotorway = true,
            source = RoadContextSource.Thoroughfare,
        )
    }

    private fun pruneCellCache(now: Long) {
        if (cellCache.size < MAX_CELL_CACHE) return
        val staleKeys = cellCache.filterValues { now - it.atMs >= cacheTtlMs }.keys
        staleKeys.forEach { cellCache.remove(it) }
        if (cellCache.size >= MAX_CELL_CACHE) {
            val oldest = cellCache.entries.sortedBy { it.value.atMs }.take(cellCache.size / 2)
            oldest.forEach { cellCache.remove(it.key) }
        }
    }

    private data class CachedResult(
        val lat: Double,
        val lon: Double,
        val atMs: Long,
        val context: RoadContext,
    )

    companion object {
        const val DEFAULT_CACHE_TTL_MS = 3 * 60 * 1000L
        const val DEFAULT_CACHE_REUSE_KM = 0.5
        private const val MAX_CELL_CACHE = 256

        /** ~100 m cells at mid-latitudes. */
        fun cellKey(latitude: Double, longitude: Double): Long {
            val latCell = (latitude * 1000.0).toInt()
            val lonCell = (longitude * 1000.0).toInt()
            return (latCell.toLong() shl 32) or (lonCell.toLong() and 0xffffffffL)
        }
    }
}
