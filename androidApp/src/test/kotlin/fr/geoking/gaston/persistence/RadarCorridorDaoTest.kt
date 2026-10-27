package fr.geoking.gaston.persistence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RadarCorridorDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: RadarCorridorDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.radarCorridorDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testInsertAndGetRadarCorridor() = runBlocking {
        val entity = RadarCorridorEntity(
            poiId = "radar-123",
            zoneRadiusMeters = 1000.0,
            fillArgb = 0x330000FF.toInt(),
            strokeArgb = 0xFF0000FF.toInt(),
            pointsJson = "[[48.8566,2.3522],[48.8570,2.3525]]",
            updatedAtMs = System.currentTimeMillis()
        )

        dao.insertCorridors(listOf(entity))

        val retrieved = dao.getCorridor("radar-123")
        assertNotNull(retrieved)
        assertEquals("radar-123", retrieved?.poiId)
        assertEquals(1000.0, retrieved?.zoneRadiusMeters ?: 0.0, 0.001)

        val batch = dao.getCorridorsForPois(listOf("radar-123", "nonexistent"))
        assertEquals(1, batch.size)
        assertEquals("radar-123", batch[0].poiId)
    }

    @Test
    fun testDeleteOlderThanAndClear() = runBlocking {
        val now = System.currentTimeMillis()
        val oldEntity = RadarCorridorEntity(
            poiId = "old-1",
            zoneRadiusMeters = 500.0,
            fillArgb = 0,
            strokeArgb = 0,
            pointsJson = "[]",
            updatedAtMs = now - 10000
        )
        val newEntity = RadarCorridorEntity(
            poiId = "new-1",
            zoneRadiusMeters = 500.0,
            fillArgb = 0,
            strokeArgb = 0,
            pointsJson = "[]",
            updatedAtMs = now
        )

        dao.insertCorridors(listOf(oldEntity, newEntity))

        dao.deleteOlderThan(now - 5000)
        assertNull(dao.getCorridor("old-1"))
        assertNotNull(dao.getCorridor("new-1"))

        dao.clear()
        assertNull(dao.getCorridor("new-1"))
    }
}
