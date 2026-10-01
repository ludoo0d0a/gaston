package fr.geoking.gaston.auto

import android.content.Intent
import android.util.Log
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import fr.geoking.gaston.BuildConfig
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
import fr.geoking.gaston.feature.location.LocationHelper
import fr.geoking.gaston.feature.notification.NotificationHelper
import fr.geoking.gaston.intent.IntentNavigationHelper
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.radar.AndroidRadarAudioNotifier
import fr.geoking.gaston.radar.DangerZoneAlertManager
import fr.geoking.gaston.repository.FuelForecastRepository
import fr.geoking.gaston.shared.network.NetworkService
import fr.geoking.gaston.toll.TollCalculator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    private val notificationHelper: NotificationHelper by inject()

    private var cachedMapDeps: MapDeps? = null
    private var dangerZoneAlertJob: Job? = null
    private var dangerZoneAlertManager: DangerZoneAlertManager? = null
    private var dangerZoneAudioNotifier: AndroidRadarAudioNotifier? = null

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                startDangerZoneAlertLoop()
            }

            override fun onStop(owner: LifecycleOwner) {
                stopDangerZoneAlertLoop()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                dangerZoneAudioNotifier?.shutdown()
                dangerZoneAudioNotifier = null
                dangerZoneAlertManager = null
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

    private fun startDangerZoneAlertLoop() {
        if (dangerZoneAlertJob?.isActive == true) return
        if (!BuildConfig.AAC_ALERTS_AVAILABLE || BuildConfig.AAC_ALERTS_KILL_SWITCH) return

        val audio = dangerZoneAudioNotifier
            ?: AndroidRadarAudioNotifier(carContext.applicationContext).also {
                dangerZoneAudioNotifier = it
            }
        val manager = dangerZoneAlertManager
            ?: DangerZoneAlertManager(settingsManager, audio, notificationHelper).also {
                dangerZoneAlertManager = it
            }

        dangerZoneAlertJob = lifecycleScope.launch {
            while (isActive) {
                val enabled = settingsManager.settings.value.radarWarningEnabled
                if (enabled) {
                    val mapDeps = getMapDeps()
                    val loc = LocationHelper.getCurrentLocation(carContext)
                    val zoneRepo = mapDeps?.dangerZoneRepository
                    if (loc != null && zoneRepo != null) {
                        try {
                            val zones = zoneRepo.zonesNear(
                                latitude = loc.latitude,
                                longitude = loc.longitude,
                                radiusKm = 25.0,
                            )
                            manager.evaluateAndAlert(loc, zones)
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            Log.w(TAG, "Danger zone alert check error", e)
                        }
                    }
                }
                delay(2000)
            }
        }
    }

    private fun stopDangerZoneAlertLoop() {
        dangerZoneAlertJob?.cancel()
        dangerZoneAlertJob = null
        dangerZoneAlertManager?.clearAlerts()
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
        try {
            inAppUpdateHelper.checkForUpdate()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check for update in CarAppSession", e)
        }

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

    companion object {
        private const val TAG = "CarAppSession"
    }
}
