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
    fun testOnGetTemplate_returnsGridTemplateWithMapTileAndAlertHeaderActions() {
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
        assertEquals(
            "Header end actions hold radar + traffic toggles (replaces deprecated ActionStrip)",
            2,
            header!!.endHeaderActions.size,
        )
    }

    @Test
    fun testDetectionToggles_layerAndAlert_noneMeansNoAlerts() {
        // Defaults: nothing selected → no meteo layer, no radar/traffic alerts
        assertTrue("weather" !in settingsManager.settings.value.selectedOverpassAmenityTypes)
        assertTrue(!settingsManager.settings.value.radarWarningEnabled)
        assertTrue(!settingsManager.settings.value.trafficAlertEnabled)
        assertTrue(!settingsManager.settings.value.mapTrafficEnabled)

        settingsManager.setMeteoDetectionEnabled(true)
        assertTrue("weather" in settingsManager.settings.value.selectedOverpassAmenityTypes)

        settingsManager.setRadarDetectionEnabled(true)
        assertTrue(settingsManager.settings.value.radarWarningEnabled)
        assertTrue("speed_camera" in settingsManager.settings.value.selectedOverpassAmenityTypes)

        settingsManager.setTrafficDetectionEnabled(true)
        assertTrue(settingsManager.settings.value.mapTrafficEnabled)
        assertTrue(settingsManager.settings.value.trafficAlertEnabled)

        settingsManager.setMeteoDetectionEnabled(false)
        settingsManager.setRadarDetectionEnabled(false)
        settingsManager.setTrafficDetectionEnabled(false)
        assertTrue("weather" !in settingsManager.settings.value.selectedOverpassAmenityTypes)
        assertTrue(!settingsManager.settings.value.radarWarningEnabled)
        assertTrue(!settingsManager.settings.value.trafficAlertEnabled)
        assertTrue(!settingsManager.settings.value.mapTrafficEnabled)
    }

    @Test
    fun testOnGetTemplate_includesRadarAndTrafficHeaderActions() {
        val carContext = CarScreenTestHarness.newTestCarContext()
        val screen = AutoDashboardScreen(
            carContext = carContext,
            settingsManager = settingsManager,
            networkService = networkService,
            fuelForecastRepository = fuelForecastRepository,
            connectivityManager = connectivityManager,
            getMapDeps = { fakeMapDeps() }
        )

        val template = screen.onGetTemplate() as GridTemplate
        val header = template.header
        assertNotNull("Header should hold radar + traffic detection toggles", header)
        assertEquals(2, header!!.endHeaderActions.size)
    }
}

class FakeDashboardNetworkService : NetworkService {
    override val status: StateFlow<NetworkStatus> = MutableStateFlow(NetworkStatus())
    override suspend fun getCurrentStatus(): NetworkStatus = NetworkStatus()
}
