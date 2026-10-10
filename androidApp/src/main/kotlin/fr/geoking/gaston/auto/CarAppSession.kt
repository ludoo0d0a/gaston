package fr.geoking.gaston.auto

import android.content.Intent
import android.util.Log
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import fr.geoking.gaston.ParkedCarIntents
import fr.geoking.gaston.R
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
import fr.geoking.gaston.parked.AaPostSessionParkSuggester
import fr.geoking.gaston.parked.ParkCandidateActions
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.repository.FuelForecastRepository
import fr.geoking.gaston.shared.network.NetworkService
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
    private val inAppUpdateHelper: fr.geoking.gaston.update.InAppUpdateHelper by inject()
    private val parkSuggester: AaPostSessionParkSuggester by inject()
    private val parkCandidateActions: ParkCandidateActions by inject()

    private var cachedMapDeps: MapDeps? = null
    private var sessionStartedAtElapsedMs: Long = 0L

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (sessionStartedAtElapsedMs == 0L) {
                    sessionStartedAtElapsedMs = android.os.SystemClock.elapsedRealtime()
                }
                // Park stop HUN + TTS: process-wide via CarConnection in AaPostSessionParkSuggester
                // (works on any AA screen and when another car app is foreground).
            }

            override fun onDestroy(owner: LifecycleOwner) {
                // Fallback if projection monitor did not retain a park suggestion this session.
                parkSuggester.startIfEligible(sessionStartedAtElapsedMs = sessionStartedAtElapsedMs)
            }
        })
    }

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
                    tollEstimateService = get(),
                    tollRouteComparer = get(),
                    osrmRoutingClient = get(),
                    geocodingClient = get<GeocodingClient>(),
                    dangerZoneRepository = get(),
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load map dependencies", e)
                return null
            }
        }
        return cachedMapDeps
    }

    override fun onNewIntent(intent: Intent) {
        screenForParkIntent(intent)?.let { screen ->
            carContext.getCarService(androidx.car.app.ScreenManager::class.java).push(screen)
            return
        }
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
        try {
            inAppUpdateHelper.checkForUpdate()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check for update in CarAppSession", e)
        }

        screenForParkIntent(intent)?.let { return it }

        val nav = IntentNavigationHelper.parseNavIntent(intent)
        if (nav != null) {
            val mapDeps = getMapDeps()
            if (mapDeps == null) {
                return ErrorScreen(
                    carContext,
                    errorMessage = carContext.getString(R.string.error_map_components),
                    errorDetail = carContext.getString(R.string.error_map_dependencies),
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
            AutoDashboardScreen(
                carContext = carContext,
                settingsManager = settingsManager,
                networkService = networkService,
                fuelForecastRepository = fuelForecastRepository,
                connectivityManager = connectivityManager,
                getMapDeps = this::getMapDeps
            )
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

    private fun screenForParkIntent(intent: Intent): Screen? = when (intent.action) {
        ParkedCarIntents.ACTION_REMEMBER -> AutoRememberParkedCarScreen(carContext, settingsManager)
        ParkedCarIntents.ACTION_SAVE_CANDIDATE -> {
            val vehicleId = intent.getStringExtra(ParkedCarIntents.EXTRA_VEHICLE_ID)
            val lat = intent.getDoubleExtra(ParkedCarIntents.EXTRA_LATITUDE, Double.NaN)
            val lon = intent.getDoubleExtra(ParkedCarIntents.EXTRA_LONGITUDE, Double.NaN)
            val saved = parkCandidateActions.save(
                vehicleId = vehicleId,
                latitude = lat.takeUnless { it.isNaN() },
                longitude = lon.takeUnless { it.isNaN() },
            )
            AutoParkCandidateResultScreen(
                carContext,
                message = carContext.getString(
                    if (saved) R.string.parked_car_saved else R.string.parked_car_location_unavailable
                ),
            )
        }
        ParkedCarIntents.ACTION_IGNORE_CANDIDATE -> {
            parkCandidateActions.ignore()
            AutoParkCandidateResultScreen(
                carContext,
                message = carContext.getString(R.string.parked_car_candidate_ignored),
            )
        }
        else -> null
    }

    companion object {
        private const val TAG = "CarAppSession"
    }
}
