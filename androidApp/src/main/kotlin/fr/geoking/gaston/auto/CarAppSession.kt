package fr.geoking.gaston.auto

import android.content.Intent
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import fr.geoking.gaston.BuildConfig
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.api.belib.BorneAvailabilityProviderFactory
import fr.geoking.gaston.api.geocoding.GeocodingClient
import fr.geoking.gaston.api.routing.RoutePlanner
import fr.geoking.gaston.api.routing.RoutingClient
import fr.geoking.gaston.api.traffic.TrafficProviderFactory
import fr.geoking.gaston.api.weather.WeatherProviderFactory
import fr.geoking.gaston.shared.location.ConnectivityManager
import fr.geoking.gaston.community.CommunityPoiRepository
import fr.geoking.gaston.community.FavoritesRepository
import fr.geoking.gaston.di.MapDeps
import fr.geoking.gaston.di.MapModuleLoader
import fr.geoking.gaston.intent.IntentNavigationHelper
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.repository.FuelForecastRepository
import fr.geoking.gaston.shared.network.NetworkService
import fr.geoking.gaston.shared.network.NetworkStatus
import fr.geoking.gaston.toll.TollCalculator
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject

/**
 * Android Auto session without wiring the phone voice manager into the car (no car STT/TTS session hook).
 * Maps, routes, fuel outlook, and car-safe settings only.
 */
class CarAppSession : Session(), KoinComponent {

    private val settingsManager: SettingsManager by inject()
    private val networkService: NetworkService by inject()
    private val fuelForecastRepository: FuelForecastRepository by inject()
    private val connectivityManager: ConnectivityManager by inject()

    private var cachedMapDeps: MapDeps? = null

    fun getMapDeps(): MapDeps? {
        if (cachedMapDeps == null) {
            try {
                MapModuleLoader.ensureLoaded()
                cachedMapDeps = MapDeps(
                    poiProvider = get<PoiProvider>(),
                    availabilityProviderFactory = get<BorneAvailabilityProviderFactory>(),
                    communityRepo = get<CommunityPoiRepository>(),
                    favoritesRepo = get<FavoritesRepository>(),
                    trafficProviderFactory = get<TrafficProviderFactory>(),
                    weatherProviderFactory = get<WeatherProviderFactory>(),
                    routePlanner = get<RoutePlanner>(),
                    routingClient = get<RoutingClient>(),
                    tollCalculator = get<TollCalculator>(),
                    geocodingClient = get<GeocodingClient>()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load map dependencies", e)
                return null
            }
        }
        return cachedMapDeps
    }

    override fun onNewIntent(intent: Intent) {
        val nav = IntentNavigationHelper.parseNavIntent(intent)
        if (nav != null) {
            val mapDeps = getMapDeps()
            if (mapDeps == null) {
                Log.e(TAG, "onNewIntent: mapDeps is null")
                return
            }
            val destQuery = nav.address ?: nav.latitude?.let { "${nav.latitude}, ${nav.longitude}" } ?: ""
            carContext.getCarService(androidx.car.app.ScreenManager::class.java).push(
                AutoRoutePlanningScreen(
                    carContext = carContext,
                    routePlanner = mapDeps.routePlanner,
                    routingClient = mapDeps.routingClient,
                    poiProvider = mapDeps.poiProvider,
                    geocodingClient = mapDeps.geocodingClient,
                    settingsManager = settingsManager,
                    initialDestinationQuery = destQuery,
                    initialDestination = nav
                )
            )
        }
    }

    override fun onCreateScreen(intent: Intent): Screen {
        val nav = IntentNavigationHelper.parseNavIntent(intent)
        if (nav != null) {
            val mapDeps = getMapDeps()
            if (mapDeps == null) {
                return ErrorScreen(
                    carContext,
                    errorMessage = "Failed to load map components.",
                    errorDetail = "Dependencies could not be initialized.",
                    templateType = "Root screen"
                )
            }
            val destQuery = nav.address ?: nav.latitude?.let { "${nav.latitude}, ${nav.longitude}" } ?: ""
            return AutoRoutePlanningScreen(
                carContext = carContext,
                routePlanner = mapDeps.routePlanner,
                routingClient = mapDeps.routingClient,
                poiProvider = mapDeps.poiProvider,
                geocodingClient = mapDeps.geocodingClient,
                settingsManager = settingsManager,
                initialDestinationQuery = destQuery,
                initialDestination = nav
            )
        }
        return try {
            if (BuildConfig.IS_PLAYSTORE_DISTRIBUTION) {
                AutoPlaystoreDashboardScreen(
                    carContext = carContext,
                    settingsManager = settingsManager,
                    networkService = networkService,
                    fuelForecastRepository = fuelForecastRepository,
                    connectivityManager = connectivityManager,
                    getMapDeps = this::getMapDeps
                )
            } else {
                AutoDashboardScreen(
                    carContext = carContext,
                    settingsManager = settingsManager,
                    networkService = networkService,
                    fuelForecastRepository = fuelForecastRepository,
                    connectivityManager = connectivityManager,
                    getMapDeps = this::getMapDeps
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create Android Auto root screen", e)
            ErrorScreen(
                carContext,
                errorMessage = e.message ?: e.toString(),
                errorDetail = e.stackTraceToString().take(300),
                templateType = "Root screen"
            )
        }
    }

    companion object {
        private const val TAG = "CarAppSession"
    }
}
