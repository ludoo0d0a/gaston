package fr.geoking.gaston.traffic

import android.content.Context
import android.util.Log
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.api.traffic.TrafficProviderFactory
import fr.geoking.gaston.api.traffic.TrafficRequest
import fr.geoking.gaston.di.MapModuleLoader
import fr.geoking.gaston.feature.location.LocationHelper
import fr.geoking.gaston.feature.notification.NotificationHelper
import fr.geoking.gaston.radar.AndroidRadarAudioNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * Process-wide traffic approach alerts (beep + TTS + AA-only HUN).
 * Starts at app launch; GPS loop runs only while [SettingsManager] `trafficAlertEnabled` is true
 * (AA dashboard / map settings via [SettingsManager.setTrafficDetectionEnabled]).
 */
class TrafficAlertCoordinator(
    context: Context,
    private val settingsManager: SettingsManager,
    private val notificationHelper: NotificationHelper,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioNotifier = AndroidRadarAudioNotifier(appContext)
    private val manager = TrafficAlertManager(
        settingsManager = settingsManager,
        audioNotifier = audioNotifier,
        notificationHelper = notificationHelper,
    )

    private var loopJob: Job? = null

    init {
        scope.launch {
            settingsManager.settings
                .map { it.trafficAlertEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    if (enabled) startLoop() else stopLoop()
                }
        }
    }

    private fun startLoop() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            Log.i(TAG, "Traffic alert loop started")
            while (isActive) {
                if (!settingsManager.settings.value.trafficAlertEnabled) break
                try {
                    MapModuleLoader.ensureLoaded()
                    val factory = GlobalContext.get().get<TrafficProviderFactory>()
                    val loc = LocationHelper.getCurrentLocation(appContext)
                    if (loc != null) {
                        val events = loadNearbyEvents(factory, loc.latitude, loc.longitude)
                        manager.evaluateAndAlert(loc, events)
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.w(TAG, "Traffic alert check error", e)
                }
                delay(POLL_MS)
            }
            manager.clearAlerts()
            Log.i(TAG, "Traffic alert loop stopped")
        }
    }

    private fun stopLoop() {
        loopJob?.cancel()
        loopJob = null
        manager.clearAlerts()
    }

    private suspend fun loadNearbyEvents(
        factory: TrafficProviderFactory,
        lat: Double,
        lon: Double,
    ): List<fr.geoking.gaston.api.traffic.TrafficEvent> {
        val settings = settingsManager.settings.value
        val providers = factory.getProviders(
            latitude = lat,
            longitude = lon,
            selectedProviders = settings.selectedPoiProviders,
        )
        if (providers.isEmpty()) return emptyList()
        val delta = FETCH_RADIUS_DEG
        val info = TrafficProviderFactory.mergeTraffic(
            providers,
            TrafficRequest.Bbox(
                latMin = lat - delta,
                lonMin = lon - delta,
                latMax = lat + delta,
                lonMax = lon + delta,
            ),
        )
        return info?.events.orEmpty()
    }

    companion object {
        private const val TAG = "TrafficAlertCoord"
        private const val POLL_MS = 12_000L
        /** ~5–6 km box so 2 km thresholds have coverage. */
        private const val FETCH_RADIUS_DEG = 0.05
    }
}
