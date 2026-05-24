package fr.geoking.gaston.api.routing

import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.poi.PoiSearchRequest
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Plans a route and returns charging stations (POIs) along the route.
 * Samples points every [sampleIntervalKm] km, fetches nearby POIs, and deduplicates.
 */
class RoutePlanner(
    private val routingClient: RoutingClient,
    private val defaultSampleIntervalKm: Double = 30.0,
    private val defaultPoiRadiusMeters: Int = 5000
) {

    /**
     * Returns POIs (gas, IRVE, truck stops, rest areas, etc.) along the route from origin to destination.
     * Uses [poiProvider] to fetch POIs near sampled points; categories are vehicle-aware when the provider
     * uses settings (e.g. SelectorPoiProvider). No range simulation; just "POIs along route".
     */
    /**
     * Returns a [Flow] that emits incremental lists of POIs along the route.
     */
    fun getStationsAlongRouteFlow(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        poiProvider: PoiProvider,
        radiusMeters: Int = defaultPoiRadiusMeters
    ): Flow<List<Poi>> = channelFlow {
        val route = routingClient.getRoute(originLat, originLon, destLat, destLon)
            ?: throw Exception("No route found")
        getStationsAlongRouteFlow(route.points, poiProvider, radiusMeters).collect {
            send(it)
        }
    }

    /**
     * Returns a [Flow] that emits incremental lists of POIs along the provided route points.
     */
    fun getStationsAlongRouteFlow(
        points: List<Pair<Double, Double>>,
        poiProvider: PoiProvider,
        radiusMeters: Int = defaultPoiRadiusMeters
    ): Flow<List<Poi>> = channelFlow {
        if (points.size < 2) {
            send(emptyList())
            return@channelFlow
        }

        // Sampling interval: try to cover the route with overlapping search circles.
        // If radius is 2km, we sample every 3km.
        val intervalMeters = (radiusMeters * 1.5).coerceIn(500.0, defaultSampleIntervalKm * 1000)

        val sampled = samplePointsByDistance(points, intervalMeters)
        val seenIds = mutableSetOf<String>()
        val resultPois = mutableListOf<Poi>()
        val mutex = Mutex()
        val semaphore = Semaphore(5) // Limit concurrent provider searches
        val request = PoiSearchRequest(latitude = 0.0, longitude = 0.0, categories = emptySet())

        coroutineScope {
            for ((lat, lon) in sampled) {
                launch {
                    semaphore.withPermit {
                        poiProvider.searchFlow(request.copy(latitude = lat, longitude = lon)).collect { res ->
                            var changed = false
                            mutex.withLock {
                                for (poi in res.pois) {
                                    if (poi.id !in seenIds) {
                                        val dist = haversineMeters(lat, lon, poi.latitude, poi.longitude)
                                        if (dist <= radiusMeters) {
                                            seenIds.add(poi.id)
                                            resultPois.add(poi)
                                            changed = true
                                        }
                                    }
                                }
                            }
                            if (changed) {
                                val currentList = mutex.withLock { resultPois.toList() }
                                send(currentList)
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun getStationsAlongRoute(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        poiProvider: PoiProvider,
        radiusMeters: Int = defaultPoiRadiusMeters
    ): Result<List<Poi>> {
        return try {
            val result = mutableListOf<Poi>()
            getStationsAlongRouteFlow(originLat, originLon, destLat, destLon, poiProvider, radiusMeters)
                .collect { result.clear(); result.addAll(it) }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getStationsAlongRoute(
        points: List<Pair<Double, Double>>,
        poiProvider: PoiProvider,
        radiusMeters: Int = defaultPoiRadiusMeters
    ): Result<List<Poi>> {
        return try {
            val result = mutableListOf<Poi>()
            getStationsAlongRouteFlow(points, poiProvider, radiusMeters)
                .collect { result.clear(); result.addAll(it) }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun samplePointsByDistance(points: List<Pair<Double, Double>>, intervalMeters: Double): List<Pair<Double, Double>> {
        if (points.isEmpty()) return emptyList()
        if (points.size == 1) return points
        val sampled = mutableListOf<Pair<Double, Double>>()
        sampled.add(points.first())
        var acc = 0.0
        for (i in 1 until points.size) {
            val (lat0, lon0) = points[i - 1]
            val (lat1, lon1) = points[i]
            val segLen = haversineMeters(lat0, lon0, lat1, lon1)
            acc += segLen
            if (acc >= intervalMeters) {
                sampled.add(lat1 to lon1)
                acc = 0.0
            }
        }
        if (sampled.last() != points.last()) {
            sampled.add(points.last())
        }
        return sampled
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val rad = PI / 180.0
        val dLat = (lat2 - lat1) * rad
        val dLon = (lon2 - lon1) * rad
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1 * rad) * cos(lat2 * rad) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1.0 - a))
        return r * c
    }
}
