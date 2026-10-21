package fr.geoking.gaston.road

import fr.geoking.gaston.persistence.RoadCacheDao
import fr.geoking.gaston.persistence.RoadCacheRegionEntity
import fr.geoking.gaston.persistence.RoadPolylineEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Room3-backed repository for fetching, caching, and serving Overpass road vector polylines.
 * Uses separate tables for covered bounding-box regions ([RoadCacheRegionEntity]) and
 * individual road polyline geometries ([RoadPolylineEntity]).
 */
class RoomRoadGeometryRepository(
    private val delegate: RoadGeometryRepository,
    private val roadCacheDao: RoadCacheDao,
    private val cacheValidityMs: Long = DEFAULT_CACHE_VALIDITY_MS,
) {
    companion object {
        /** Default cache validity: 7 days. */
        const val DEFAULT_CACHE_VALIDITY_MS = 7 * 24 * 3600 * 1000L
    }

    private val mutex = Mutex()

    /**
     * Fetches drivable road polylines around [latitude]/[longitude] within [radiusKm].
     * Checks Room database first. On cache miss, queries Overpass API via [delegate] and
     * persists results to Room3 tables.
     */
    suspend fun fetchRoadsNear(
        latitude: Double,
        longitude: Double,
        radiusKm: Double = 1.0,
    ): List<RoadPolyline> = mutex.withLock {
        val deltaLat = radiusKm / 111.0
        val deltaLon = radiusKm / (111.0 * kotlin.math.cos(latitude * kotlin.math.PI / 180.0)).coerceAtLeast(0.01)

        val minLat = latitude - deltaLat
        val maxLat = latitude + deltaLat
        val minLon = longitude - deltaLon
        val maxLon = longitude + deltaLon

        val minUpdatedAtMs = System.currentTimeMillis() - cacheValidityMs

        val coveringRegion = runCatching {
            roadCacheDao.findCoveringRegion(
                latMin = minLat,
                latMax = maxLat,
                lonMin = minLon,
                lonMax = maxLon,
                minUpdatedAtMs = minUpdatedAtMs,
            )
        }.getOrNull()

        if (coveringRegion != null) {
            val cachedEntities = runCatching {
                roadCacheDao.getRoadsInBbox(
                    latMin = minLat,
                    latMax = maxLat,
                    lonMin = minLon,
                    lonMax = maxLon,
                    minUpdatedAtMs = minUpdatedAtMs,
                )
            }.getOrDefault(emptyList())

            if (cachedEntities.isNotEmpty()) {
                return cachedEntities.map { entity ->
                    RoadPolyline(
                        id = entity.id,
                        highwayType = entity.highwayType,
                        name = entity.name,
                        points = parsePointsJson(entity.pointsJson),
                    )
                }
            }
        }

        val networkPolylines = delegate.fetchRoadsNear(latitude, longitude, radiusKm)

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

        return networkPolylines
    }

    suspend fun clearCache() {
        mutex.withLock {
            runCatching {
                roadCacheDao.clearRegions()
                roadCacheDao.clearRoads()
            }
            delegate.clearCache()
        }
    }

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
