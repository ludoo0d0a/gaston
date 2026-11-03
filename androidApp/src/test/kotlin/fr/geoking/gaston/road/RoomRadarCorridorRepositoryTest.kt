package fr.geoking.gaston.road

import fr.geoking.gaston.persistence.RadarCorridorDao
import fr.geoking.gaston.persistence.RadarCorridorEntity
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomRadarCorridorRepositoryTest {

    private class InMemoryCorridorDao : RadarCorridorDao {
        val rows = mutableMapOf<String, RadarCorridorEntity>()
        override suspend fun getCorridor(poiId: String): RadarCorridorEntity? = rows[poiId]
        override suspend fun getCorridorsForPois(poiIds: List<String>): List<RadarCorridorEntity> =
            poiIds.mapNotNull { rows[it] }
        override suspend fun insertCorridors(corridors: List<RadarCorridorEntity>) {
            corridors.forEach { rows[it.poiId] = it }
        }
        override suspend fun deleteOlderThan(thresholdMs: Long) {
            rows.values.removeAll { it.updatedAtMs < thresholdMs }
        }
        override suspend fun clear() = rows.clear()
    }

    private val day = 24L * 3600 * 1000
    private val radar = Poi(
        id = "fr_radar_12014",
        name = "Zone 130 km/h",
        address = "A1",
        poiCategory = PoiCategory.Radar,
        latitude = 48.8500,
        longitude = 2.3500,
        source = "test",
        rawSourceData = mapOf("vma" to "130"),
    )

    private val storedPoints = listOf(
        10.0 to 20.0,
        10.1 to 20.0,
        10.1 to 20.1,
        10.0 to 20.0,
    )

    private fun storedEntity(updatedAtMs: Long) = RadarCorridorEntity(
        poiId = radar.id,
        zoneRadiusMeters = 800.0,
        fillArgb = 0x442563EB,
        strokeArgb = 0xFF1D4ED8.toInt(),
        pointsJson = storedPoints.joinToString(prefix = "[", postfix = "]") { (la, lo) -> "[$la,$lo]" },
        updatedAtMs = updatedAtMs,
    )

    @Test
    fun corridorEntry_lessThanAWeekOld_isServedFromCache() = runBlocking {
        val dao = InMemoryCorridorDao()
        val entry = storedEntity(System.currentTimeMillis() - 6 * day)
        dao.rows[radar.id] = entry
        val repo = RoomRadarCorridorRepository(dao)

        val result = repo.getCorridorForRadar(radar, roadPolylines = emptyList())

        assertEquals(storedPoints, result?.points)
        assertEquals(entry.updatedAtMs, dao.rows.getValue(radar.id).updatedAtMs)
    }

    @Test
    fun corridorEntry_olderThanAWeek_isRecalculatedAndRewritten() = runBlocking {
        val dao = InMemoryCorridorDao()
        val oldMs = System.currentTimeMillis() - 8 * day
        dao.rows[radar.id] = storedEntity(oldMs)
        val repo = RoomRadarCorridorRepository(dao)

        val result = repo.getCorridorForRadar(radar, roadPolylines = emptyList())

        assertTrue(result != null)
        assertNotEquals(storedPoints, result?.points)
        assertTrue(
            "expired row must be rewritten with a fresh timestamp",
            dao.rows.getValue(radar.id).updatedAtMs > oldMs,
        )
    }

    @Test
    fun validityConstant_isOneWeek() {
        assertEquals(7 * day, RoomRadarCorridorRepository.DEFAULT_CACHE_VALIDITY_MS)
    }
}
