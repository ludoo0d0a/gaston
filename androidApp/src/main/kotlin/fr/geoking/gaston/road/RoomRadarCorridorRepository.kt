package fr.geoking.gaston.road

import fr.geoking.gaston.aac.RadarRoadCorridorCalculator
import fr.geoking.gaston.aac.RadarRoadCorridorPolygon
import fr.geoking.gaston.persistence.RadarCorridorDao
import fr.geoking.gaston.persistence.RadarCorridorEntity
import fr.geoking.gaston.poi.Poi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RoomRadarCorridorRepository(
    private val corridorDao: RadarCorridorDao,
    private val cacheValidityMs: Long = DEFAULT_CACHE_VALIDITY_MS
) {
    companion object {
        const val DEFAULT_CACHE_VALIDITY_MS = 7 * 24 * 3600 * 1000L
    }

    private val mutex = Mutex()

    suspend fun getCorridorForRadar(
        poi: Poi,
        roadPolylines: List<RoadPolyline>
    ): RadarRoadCorridorPolygon? = mutex.withLock {
        val minUpdatedAtMs = System.currentTimeMillis() - cacheValidityMs
        val cached = runCatching { corridorDao.getCorridor(poi.id) }.getOrNull()

        if (cached != null && cached.updatedAtMs >= minUpdatedAtMs) {
            val points = parsePointsJson(cached.pointsJson)
            if (points.size >= 4) {
                return RadarRoadCorridorPolygon(
                    poiId = cached.poiId,
                    zoneRadiusMeters = cached.zoneRadiusMeters,
                    fillArgb = cached.fillArgb,
                    strokeArgb = cached.strokeArgb,
                    points = points
                )
            }
        }

        val calculated = RadarRoadCorridorCalculator.calculateCorridor(poi, roadPolylines) ?: return null
        val now = System.currentTimeMillis()

        runCatching {
            corridorDao.insertCorridors(
                listOf(
                    RadarCorridorEntity(
                        poiId = calculated.poiId,
                        zoneRadiusMeters = calculated.zoneRadiusMeters,
                        fillArgb = calculated.fillArgb,
                        strokeArgb = calculated.strokeArgb,
                        pointsJson = serializePointsJson(calculated.points),
                        updatedAtMs = now
                    )
                )
            )
        }

        return calculated
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
