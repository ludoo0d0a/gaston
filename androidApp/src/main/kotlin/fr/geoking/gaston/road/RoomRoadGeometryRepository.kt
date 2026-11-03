package fr.geoking.gaston.road

import fr.geoking.gaston.aac.RoadNetworkClass
import fr.geoking.gaston.persistence.RoadCacheDao
import fr.geoking.gaston.persistence.RoadCacheRegionEntity
import fr.geoking.gaston.persistence.RoadPolylineEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Room-backed [RoadGeometrySource]: serves Overpass road polylines from disk when the
 * requested bbox is already covered, otherwise delegates and persists the result.
 */
class RoomRoadGeometryRepository(
    private val delegate: RoadGeometryRepository,
    private val roadCacheDao: RoadCacheDao,
    private val cacheValidityMs: Long = DEFAULT_CACHE_VALIDITY_MS,
) : RoadGeometrySource {
    companion object {
        /** Default cache validity: 7 days. */
        const val DEFAULT_CACHE_VALIDITY_MS = 7 * 24 * 3600 * 1000L
    }

    private val mutex = Mutex()

    /**
     * Fetches drivable road polylines around [latitude]/[longitude] within [radiusKm].
     * Checks Room database first. On cache miss, queries Overpass API via [delegate] and
     * persists results to Room tables.
     */
    override suspend fun fetchRoadsNear(
        latitude: Double,
        longitude: Double,
        radiusKm: Double,
    ): List<RoadPolyline> = mutex.withLock {
        val (minLat, maxLat, minLon, maxLon) = bbox(latitude, longitude, radiusKm)
        val cached = readCachedRoads(minLat, maxLat, minLon, maxLon)
        if (cached != null) return cached

        val networkPolylines = delegate.fetchRoadsNear(latitude, longitude, radiusKm)
        persistRoads(networkPolylines, minLat, maxLat, minLon, maxLon)
        return networkPolylines
    }

    /**
     * Corridor roads: prefer Room / near-cache (filtered by [roadClass]), else class-filtered
     * Overpass via [delegate], then persist so the next radar reuses disk.
     */
    override suspend fun fetchRoadsAround(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
        roadClass: RoadNetworkClass,
    ): List<RoadPolyline> = mutex.withLock {
        val radiusKm = (radiusMeters + 150.0) / 1000.0
        val (minLat, maxLat, minLon, maxLon) = bbox(latitude, longitude, radiusKm)
        val cached = readCachedRoads(minLat, maxLat, minLon, maxLon)
        if (cached != null) {
            val filtered = cached.filter {
                RoadGeometryRepository.matchesRoadClass(it.highwayType, roadClass)
            }
            if (filtered.isNotEmpty()) return filtered
        }

        val networkPolylines = delegate.fetchRoadsAround(latitude, longitude, radiusMeters, roadClass)
        if (networkPolylines.isNotEmpty()) {
            persistRoads(networkPolylines, minLat, maxLat, minLon, maxLon)
        }
        return networkPolylines
    }

    override suspend fun clearCache() {
        mutex.withLock {
            runCatching {
                roadCacheDao.clearRegions()
                roadCacheDao.clearRoads()
            }
            delegate.clearCache()
        }
    }

    private fun bbox(
        latitude: Double,
        longitude: Double,
        radiusKm: Double,
    ): Quad {
        val deltaLat = radiusKm / 111.0
        val deltaLon = radiusKm / (111.0 * kotlin.math.cos(latitude * kotlin.math.PI / 180.0)).coerceAtLeast(0.01)
        return Quad(
            minLat = latitude - deltaLat,
            maxLat = latitude + deltaLat,
            minLon = longitude - deltaLon,
            maxLon = longitude + deltaLon,
        )
    }

    private suspend fun readCachedRoads(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
    ): List<RoadPolyline>? {
        val minUpdatedAtMs = System.currentTimeMillis() - cacheValidityMs
        runCatching {
            roadCacheDao.findCoveringRegion(
                latMin = minLat,
                latMax = maxLat,
                lonMin = minLon,
                lonMax = maxLon,
                minUpdatedAtMs = minUpdatedAtMs,
            )
        }.getOrNull() ?: return null

        val cachedEntities = runCatching {
            roadCacheDao.getRoadsInBbox(
                latMin = minLat,
                latMax = maxLat,
                lonMin = minLon,
                lonMax = maxLon,
                minUpdatedAtMs = minUpdatedAtMs,
            )
        }.getOrDefault(emptyList())

        if (cachedEntities.isEmpty()) return null
        return cachedEntities.map { entity ->
            RoadPolyline(
                id = entity.id,
                highwayType = entity.highwayType,
                name = entity.name,
                points = parsePointsJson(entity.pointsJson),
            )
        }
    }

    private suspend fun persistRoads(
        networkPolylines: List<RoadPolyline>,
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
    ) {
        val now = System.currentTimeMillis()
        val entitiesToSave = networkPolylines.map { polyline ->
            val pLats = polyline.points.map { it.first }
            val pLons = polyline.points.map { it.second }
            RoadPolylineEntity(
                id = polyline.id,
                highwayType = polyline.highwayType,
                name = polyline.name,
                pointsJson = serializePointsJson(polyline.points),
                minLat = pLats.minOrNull() ?: minLat,
                maxLat = pLats.maxOrNull() ?: maxLat,
                minLon = pLons.minOrNull() ?: minLon,
                maxLon = pLons.maxOrNull() ?: maxLon,
                updatedAtMs = now,
            )
        }
        runCatching {
            if (entitiesToSave.isNotEmpty()) {
                roadCacheDao.insertRoads(entitiesToSave)
            }
            roadCacheDao.insertRegion(
                RoadCacheRegionEntity(
                    minLat = minLat,
                    maxLat = maxLat,
                    minLon = minLon,
                    maxLon = maxLon,
                    updatedAtMs = now,
                )
            )
        }
    }

    private data class Quad(
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double,
    )

    private fun serializePointsJson(points: List<Pair<Double, Double>>): String {
        return points.joinToString(prefix = "[", postfix = "]") { (lat, lon) -> "[$lat,$lon]" }
    }

    private fun parsePointsJson(jsonStr: String): List<Pair<Double, Double>> {
        val result = mutableListOf<Pair<Double, Double>>()
        val regex = Regex("""\[\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*\]""")
        regex.findAll(jsonStr).forEach { match ->
            val lat = match.groupValues[1].toDoubleOrNull()
            val lon = match.groupValues[2].toDoubleOrNull()
            if (lat != null && lon != null) {
                result.add(lat to lon)
            }
        }
        return result
    }
}
