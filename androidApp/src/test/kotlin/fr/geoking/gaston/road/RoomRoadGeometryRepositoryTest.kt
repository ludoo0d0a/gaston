package fr.geoking.gaston.road

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.geoking.gaston.persistence.AppDatabase
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RoomRoadGeometryRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: RoomRoadGeometryRepository
    private var delegateCallCount = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(Dispatchers.IO)
            .allowMainThreadQueries()
            .build()

        delegateCallCount = 0
        val mockDelegate = object : RoadGeometryRepository(
            overpassClient = object : fr.geoking.gaston.api.overpass.OverpassClient(
                HttpClient()
            ) {}
        ) {
            override suspend fun fetchRoadsNear(
                latitude: Double,
                longitude: Double,
                radiusKm: Double
            ): List<RoadPolyline> {
                delegateCallCount++
                return listOf(
                    RoadPolyline(
                        id = 101L,
                        highwayType = "motorway",
                        name = "A4",
                        points = listOf(48.85 to 2.35, 48.86 to 2.36)
                    )
                )
            }
        }

        repo = RoomRoadGeometryRepository(
            delegate = mockDelegate,
            roadCacheDao = db.roadCacheDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun fetchRoadsNear_queriesDelegateOnCacheMiss_andCachesInRoom() = runBlocking {
        assertEquals(0, delegateCallCount)

        val roads1 = repo.fetchRoadsNear(48.85, 2.35, radiusKm = 1.0)
        assertEquals(1, delegateCallCount)
        assertEquals(1, roads1.size)
        assertEquals("A4", roads1.first().name)
        assertEquals("motorway", roads1.first().highwayType)

        // Second call should hit Room cache
        val roads2 = repo.fetchRoadsNear(48.85, 2.35, radiusKm = 1.0)
        assertEquals(1, delegateCallCount) // Call count remains 1
        assertEquals(1, roads2.size)
        assertEquals("A4", roads2.first().name)
    }

    @Test
    fun clearCache_clearsRoomCacheTables() = runBlocking {
        repo.fetchRoadsNear(48.85, 2.35, radiusKm = 1.0)
        assertEquals(1, delegateCallCount)

        repo.clearCache()

        repo.fetchRoadsNear(48.85, 2.35, radiusKm = 1.0)
        assertEquals(2, delegateCallCount)
    }
}
