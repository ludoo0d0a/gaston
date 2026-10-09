package fr.geoking.gaston.radar

import android.content.Context
import android.util.Log
import fr.geoking.gaston.BuildConfig
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.aac.DangerZoneRepository
import fr.geoking.gaston.di.MapModuleLoader
import fr.geoking.gaston.feature.location.LocationHelper
import fr.geoking.gaston.feature.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * Process-wide AAC danger-zone alert loop (same lifecycle pattern as
 * [fr.geoking.gaston.shared.location.ConnectivityManager] for border-crossing HUNs).
 *
 * Starts at app launch via Koin `createdAtStart` when AAC alerts are available.
 * GPS poll → local [DangerZoneRepository] → beep + TTS + temporary phone/AA HUN + shared HUD.
 * Always on (no user toggle); emergency off is [BuildConfig.AAC_ALERTS_KILL_SWITCH] only.
 */
class DangerZoneAlertCoordinator(
    context: Context,
    private val settingsManager: SettingsManager,
    private val notificationHelper: NotificationHelper,
    private val hudStore: DangerZoneHudStore,
    private val alertTester: DangerZoneAlertTester,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioNotifier = AndroidRadarAudioNotifier(appContext)
    private val manager = DangerZoneAlertManager(
        settingsManager = settingsManager,
        audioNotifier = audioNotifier,
        notificationHelper = notificationHelper,
        hudStore = hudStore,
    )

    private var loopJob: Job? = null

    val hudState: StateFlow<DangerZoneHudState> = manager.hudState

    init {
        if (alertsAvailable()) {
            startLoop()
        }
        scope.launch {
            // Dev test from phone / AA settings → same HUN + TTS path as live alerts.
            alertTester.requests.collect { request ->
                manager.triggerTestAlert(request.speedLimitKmH)
                if (loopJob?.isActive != true) {
                    launch {
                        delay(TEST_HUD_CLEAR_MS)
                        manager.dismissHud()
                    }
                }
            }
        }
    }

    fun triggerTestAlert(speedLimitKmH: Int? = DangerZoneAlertManager.TEST_ALERT_SPEED_LIMIT_KMH) {
        manager.triggerTestAlert(speedLimitKmH)
    }

    private fun alertsAvailable(): Boolean =
        BuildConfig.AAC_ALERTS_AVAILABLE && !BuildConfig.AAC_ALERTS_KILL_SWITCH

    private fun startLoop() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            Log.i(TAG, "Danger-zone alert loop started")
            while (isActive) {
                if (!alertsAvailable()) {
                    break
                }
                try {
                    MapModuleLoader.ensureLoaded()
                    val zoneRepo = GlobalContext.get().get<DangerZoneRepository>()
                    val loc = LocationHelper.getCurrentLocation(appContext)
                    if (loc != null) {
                        val zones = zoneRepo.zonesNear(
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            radiusKm = ZONE_CACHE_RADIUS_KM,
                        )
                        manager.evaluateAndAlert(loc, zones)
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.w(TAG, "Danger zone alert check error", e)
                }
                delay(POLL_MS)
            }
            manager.clearAlerts()
            Log.i(TAG, "Danger-zone alert loop stopped")
        }
    }

    companion object {
        private const val TAG = "DangerZoneAlertCoord"
        private const val POLL_MS = 2_000L
        private const val ZONE_CACHE_RADIUS_KM = 100.0
        private const val TEST_HUD_CLEAR_MS = 8_000L
    }
}
