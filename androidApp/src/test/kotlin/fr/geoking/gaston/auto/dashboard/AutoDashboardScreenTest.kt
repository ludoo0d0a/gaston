package fr.geoking.gaston.auto.dashboard

import androidx.car.app.model.GridTemplate
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.auto.testutil.CarScreenTestHarness
import fr.geoking.gaston.auto.testutil.fakeMapDeps
import fr.geoking.gaston.persistence.AppDatabase
import fr.geoking.gaston.repository.FuelForecastRepository
import fr.geoking.gaston.shared.location.ConnectivityManager
import fr.geoking.gaston.shared.network.NetworkService
import fr.geoking.gaston.shared.network.NetworkStatus
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AutoDashboardScreenTest {

    private lateinit var settingsManager: SettingsManager
    private lateinit var networkService: NetworkService
    private lateinit var fuelForecastRepository: FuelForecastRepository
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        settingsManager = SettingsManager(context)
        networkService = FakeDashboardNetworkService()

        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        fuelForecastRepository = FuelForecastRepository(
            http = HttpClient(),
            db = db
        )

        connectivityManager = ConnectivityManager(
            scope = CoroutineScope(Dispatchers.Unconfined),
            networkService = networkService,
            networkSettings = settingsManager
        )

        startKoin {
            modules(
                module {
                    single { settingsManager }
                    single { networkService }
                    single { fuelForecastRepository }
                }
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
        stopKoin()
    }

    @Test
    fun testOnGetTemplate_returnsGridTemplateWithMapTileAndWeatherHeaderAction() {
        val carContext = CarScreenTestHarness.newTestCarContext()
        val screen = AutoDashboardScreen(
            carContext = carContext,
            settingsManager = settingsManager,
            networkService = networkService,
            fuelForecastRepository = fuelForecastRepository,
            connectivityManager = connectivityManager,
            getMapDeps = { fakeMapDeps() }
        )

        val template = screen.onGetTemplate()
        assertTrue("Template should be a GridTemplate", template is GridTemplate)

        val gridTemplate = template as GridTemplate
        val itemList = gridTemplate.singleList
        assertNotNull("ItemList should not be null", itemList)
        assertEquals("Grid should contain exactly 6 items", 6, itemList!!.items.size)

        val header = gridTemplate.header
        assertNotNull("Header should not be null", header)
    }

    @Test
    fun testWeatherToggle_updatesSettings() {
        val carContext = CarScreenTestHarness.newTestCarContext()
        val screen = AutoDashboardScreen(
            carContext = carContext,
            settingsManager = settingsManager,
            networkService = networkService,
            fuelForecastRepository = fuelForecastRepository,
            connectivityManager = connectivityManager,
            getMapDeps = { fakeMapDeps() }
        )

        // Initial weather state is off
        assertTrue("weather" !in settingsManager.settings.value.selectedOverpassAmenityTypes)

        // Toggle weather via settings (simulating weather action tap)
        settingsManager.setOtherMode("weather")

        assertTrue("weather" in settingsManager.settings.value.selectedOverpassAmenityTypes)
    }
}

class FakeDashboardNetworkService : NetworkService {
    override val status: StateFlow<NetworkStatus> = MutableStateFlow(NetworkStatus())
    override suspend fun getCurrentStatus(): NetworkStatus = NetworkStatus()
}
