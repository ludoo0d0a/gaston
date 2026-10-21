package fr.geoking.gaston.road

import fr.geoking.gaston.api.overpass.OverpassClient
import fr.geoking.gaston.shared.location.haversineKm
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Represents a drivable road polyline retrieved from vector data (e.g. OpenStreetMap ways).
 */
data class RoadPolyline(
    val id: Long,
    val highwayType: String?,
    val name: String?,
    /** List of (lat, lon) coordinates along the road polyline. */
    val points: List<Pair<Double, Double>>,
)

/**
 * Repository for fetching and caching drivable road vector line geometries from Overpass API.
 */
open class RoadGeometryRepository(
    private val overpassClient: OverpassClient,
) {
    private val mutex = Mutex()
    private var cachedCenterLat: Double? = null
    private var cachedCenterLon: Double? = null
    private var cachedRadiusKm: Double = 0.0
    private var cachedPolylines: List<RoadPolyline> = emptyList()

    /**
     * Fetches drivable road polylines around [latitude]/[longitude] within [radiusKm].
     * Returns cached polylines if the requested area is already covered.
     */
    open suspend fun fetchRoadsNear(
        latitude: Double,
        longitude: Double,
        radiusKm: Double = 1.0,
    ): List<RoadPolyline> {
        mutex.withLock {
            val cLat = cachedCenterLat
            val cLon = cachedCenterLon
            if (cLat != null && cLon != null) {
                val distKm = haversineKm(latitude, longitude, cLat, cLon)
                if (distKm + radiusKm <= cachedRadiusKm && cachedPolylines.isNotEmpty()) {
                    return cachedPolylines
                }
            }
        }

        val deltaLat = radiusKm / 111.0
        val deltaLon = radiusKm / (111.0 * kotlin.math.cos(latitude * kotlin.math.PI / 180.0)).coerceAtLeast(0.01)

        val minLat = latitude - deltaLat
        val maxLat = latitude + deltaLat
        val minLon = longitude - deltaLon
        val maxLon = longitude + deltaLon

        val ways = runCatching {
            overpassClient.queryDrivableRoadWaysInBbox(
                minLat = minLat,
                minLon = minLon,
                maxLat = maxLat,
                maxLon = maxLon,
                limit = 500,
            )
        }.getOrDefault(emptyList())

        val polylines = ways.map { way ->
            RoadPolyline(
                id = way.id,
                highwayType = way.tags["highway"],
                name = way.tags["name"],
                points = way.points,
            )
        }

        mutex.withLock {
            cachedCenterLat = latitude
            cachedCenterLon = longitude
            cachedRadiusKm = radiusKm
            cachedPolylines = polylines
        }

        return polylines
    }

    /** Clears the in-memory cache of road polylines. */
    open suspend fun clearCache() {
        mutex.withLock {
            cachedCenterLat = null
            cachedCenterLon = null
            cachedRadiusKm = 0.0
            cachedPolylines = emptyList()
        }
    }
}
