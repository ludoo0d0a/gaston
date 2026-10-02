package fr.geoking.gaston.feature.maintenance

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.geoking.gaston.persistence.AppDatabase
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
class MaintenanceRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: MaintenanceRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = MaintenanceRepository(db.maintenanceEventDao(), db.serviceIntervalDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun upsertAndListByVehicle() = runBlocking {
        val e1 = MaintenanceEvent(
            id = "a",
            vehicleId = "v1",
            type = MaintenanceEventType.FUEL,
            dateEpochMs = 200,
            volumeLiters = 40.0,
        )
        val e2 = MaintenanceEvent(
            id = "b",
            vehicleId = "v2",
            type = MaintenanceEventType.SERVICE,
            dateEpochMs = 100,
            serviceKind = ServiceKind.OIL_CHANGE,
        )
        repo.upsertEvent(e1)
        repo.upsertEvent(e2)
        assertEquals(1, repo.listEvents("v1").size)
        assertEquals(MaintenanceEventType.FUEL, repo.listEvents("v1").first().type)
        assertEquals(1, repo.listEvents("v2").size)
    }

    @Test
    fun deleteAllForVehicle_clearsEventsAndIntervals() = runBlocking {
        repo.ensureDefaultIntervals("v1")
        repo.upsertEvent(
            MaintenanceEvent(
                id = "e",
                vehicleId = "v1",
                type = MaintenanceEventType.ODOMETER,
                dateEpochMs = 1,
                odometerKm = 1000,
            )
        )
        assertTrue(repo.listIntervals("v1").isNotEmpty())
        repo.deleteAllForVehicle("v1")
        assertTrue(repo.listEvents("v1").isEmpty())
        assertTrue(repo.listIntervals("v1").isEmpty())
    }

    @Test
    fun serviceEvent_updatesMatchingInterval() = runBlocking {
        repo.ensureDefaultIntervals("v1")
        val event = MaintenanceEvent(
            id = "svc",
            vehicleId = "v1",
            type = MaintenanceEventType.SERVICE,
            dateEpochMs = 1_000L,
            odometerKm = 12_000,
            serviceKind = ServiceKind.OIL_CHANGE,
        )
        repo.upsertEvent(event)
        val oil = repo.listIntervals("v1").first { it.serviceKind == ServiceKind.OIL_CHANGE }
        assertEquals(12_000, oil.lastOdometerKm)
        assertEquals(1_000L, oil.lastDoneAtMs)
        assertEquals("svc", oil.lastEventId)
    }
}
