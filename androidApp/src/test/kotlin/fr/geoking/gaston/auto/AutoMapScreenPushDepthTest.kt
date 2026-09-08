package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.test.core.app.ApplicationProvider
import fr.geoking.gaston.CarMapMode
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.auto.mapsforge.MapsforgeMapManager
import fr.geoking.gaston.auto.mapsforge.MapsforgeStationDetailScreen
import fr.geoking.gaston.auto.testutil.CarScreenTestHarness
import fr.geoking.gaston.auto.testutil.fakeMapDeps
import fr.geoking.gaston.poi.Poi
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner

/**
 * Regression guard for the host's 5-template-step-per-task quota (category.POI apps, see
 * docs/android-auto.md). For each of the 6 CarMapModes, builds the real dashboard -> energy dashboard
 * -> map -> station-detail push chain and asserts it never exceeds the budget.
 *
 * The dashboard root screen itself (AutoDashboardScreen, step 1) is not constructed here — it needs a
 * Room-backed FuelForecastRepository that's painful to build in a JVM test — so it's accounted for as
 * an implicit extra step instead of being pushed for real.
 */
@RunWith(RobolectricTestRunner::class)
class AutoMapScreenPushDepthTest {

    private val implicitDashboardRootSteps = 1
    private val maxTemplateSteps = 5

    @Before
    fun startTestKoin() {
        // CarMapLibreRenderer (used by MapLibre/MapTiler/Protomaps screens and MapLibreStationDetailScreen)
        // resolves its own SettingsManager from the global Koin container.
        startKoin {
            modules(module { single { SettingsManager(ApplicationProvider.getApplicationContext()) } })
        }
    }

    @After
    fun stopTestKoin() {
        stopKoin()
    }

    private fun samplePoi() = Poi(
        id = "poi-1",
        name = "Test station",
        address = "",
        latitude = 48.8566,
        longitude = 2.3522,
    )

    @Test
    fun everyMapModePushChainStaysWithinTemplateQuota() {
        for (mode in CarMapMode.entries) {
            val carContext = CarScreenTestHarness.newTestCarContext()
            val settingsManager = SettingsManager(ApplicationProvider.getApplicationContext())
            settingsManager.setCarMapMode(mode)
            val screenManager = CarScreenTestHarness.screenManagerOf(carContext)
            val mapDeps = fakeMapDeps()

            // Step 2: energy dashboard (the dashboard root itself is the implicit step 1).
            screenManager.push(AutoFuelDashboardScreen(carContext, settingsManager) { mapDeps })

            // Step 3: the map screen for this mode.
            screenManager.push(AutoMapScreenFactory.createMapPoiScreen(carContext, mapDeps, settingsManager))

            // Step 4: the mode's station-detail screen.
            screenManager.push(stationDetailScreenFor(mode, carContext, settingsManager))

            val totalSteps = implicitDashboardRootSteps + screenManager.screensPushed.size
            assertTrue(
                "mode=$mode pushed $totalSteps steps (incl. implicit dashboard root), exceeds host quota of $maxTemplateSteps",
                totalSteps <= maxTemplateSteps,
            )
        }
    }

    private fun stationDetailScreenFor(
        mode: CarMapMode,
        carContext: CarContext,
        settingsManager: SettingsManager,
    ): Screen {
        val poi = samplePoi()
        val availability: StationAvailabilitySummary? = null
        return when (mode) {
            CarMapMode.Native -> PlaceListMapStationDetailScreen(
                carContext = carContext,
                poi = poi,
                availability = availability,
                searchLat = poi.latitude,
                searchLon = poi.longitude,
                effectiveEnergies = emptySet(),
                effectivePowerLevels = emptySet(),
            )

            CarMapMode.Custom -> CustomMapStationDetailScreen(
                carContext = carContext,
                poi = poi,
                availability = availability,
                searchLat = poi.latitude,
                searchLon = poi.longitude,
                zoom = AutoMapCamera.DEFAULT_ZOOM,
                orientationMode = MapOrientationMode.HeadingUp,
                bearing = 0f,
                effectiveEnergies = emptySet(),
                effectivePowerLevels = emptySet(),
                settingsManager = settingsManager,
            )

            CarMapMode.MapLibre, CarMapMode.MapTiler, CarMapMode.Protomaps -> MapLibreStationDetailScreen(
                carContext = carContext,
                poi = poi,
                availability = availability,
                searchLat = poi.latitude,
                searchLon = poi.longitude,
                zoom = AutoMapCamera.DEFAULT_ZOOM,
                orientationMode = MapOrientationMode.HeadingUp,
                bearing = 0f,
                effectiveEnergies = emptySet(),
                effectivePowerLevels = emptySet(),
                settingsManager = settingsManager,
            )

            CarMapMode.Mapsforge -> MapsforgeStationDetailScreen(
                carContext = carContext,
                poi = poi,
                availability = availability,
                searchLat = poi.latitude,
                searchLon = poi.longitude,
                zoom = AutoMapCamera.DEFAULT_ZOOM,
                orientationMode = MapOrientationMode.HeadingUp,
                bearing = 0f,
                effectiveEnergies = emptySet(),
                effectivePowerLevels = emptySet(),
                settingsManager = settingsManager,
                mapManager = MapsforgeMapManager(carContext),
            )
        }
    }
}
