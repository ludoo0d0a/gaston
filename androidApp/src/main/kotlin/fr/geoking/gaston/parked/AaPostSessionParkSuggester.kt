package fr.geoking.gaston.parked

import android.content.Context
import android.util.Log
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.activeVehicle
import fr.geoking.gaston.feature.location.LocationHelper
import fr.geoking.gaston.feature.notification.NotificationHelper
import fr.geoking.gaston.parkedPositionFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Park suggestion while Android Auto / car mode keeps [ParkStopMonitorService] alive:
 * 1. **While projected (FGS)**: vehicle speed then ~10s stop → HUN + TTS + Save.
 * 2. **Projection ends / Gaston session destroy** (fallback): if nothing retained → 10s gate + propose.
 */
class AaPostSessionParkSuggester(
    context: Context,
    private val settingsManager: SettingsManager,
    private val notificationHelper: NotificationHelper,
    private val candidateStore: ParkCandidateStore,
    private val walkAwayMonitor: ParkWalkAwayMonitor,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audio = ParkSuggestionAudio(appContext)
    private var projectionJob: Job? = null
    private var postSessionJob: Job? = null
    private var projectionStartedAtElapsedMs: Long = 0L
    private var wasProjected: Boolean = false

    private val carConnection = CarConnection(appContext)
    private val connectionObserver = Observer<Int> { type ->
        when (type) {
            CarConnection.CONNECTION_TYPE_PROJECTION,
            CarConnection.CONNECTION_TYPE_NATIVE,
            -> onProjected()
            else -> onProjectionEnded()
        }
    }

    init {
        // Process-wide: FGS keeps us alive when another AA app is foreground.
        carConnection.type.observeForever(connectionObserver)
    }

    private fun onProjected() {
        if (projectionStartedAtElapsedMs == 0L) {
            projectionStartedAtElapsedMs = android.os.SystemClock.elapsedRealtime()
        }
        wasProjected = true
        ParkStopMonitorService.start(appContext)
    }

    private fun onProjectionEnded() {
        if (!wasProjected) {
            ParkStopMonitorService.stop(appContext)
            return
        }
        wasProjected = false
        val started = projectionStartedAtElapsedMs
        projectionStartedAtElapsedMs = 0L
        ParkStopMonitorService.stop(appContext)
        // Fallback when AA disconnects (Gaston may never have been opened this trip).
        startIfEligible(sessionStartedAtElapsedMs = started)
    }

    /** Called from [ParkStopMonitorService] after startForeground. */
    fun attachFromService() {
        startProjectionMonitoring()
    }

    /** Called from [ParkStopMonitorService] onDestroy. */
    fun detachFromService() {
        stopProjectionMonitoring()
    }

    /** GPS polling for stop detection while the FGS is running. */
    fun startProjectionMonitoring() {
        if (projectionJob?.isActive == true) return
        postSessionJob?.cancel()
        val tracker = InSessionStopTracker()
        projectionJob = scope.launch {
            Log.i(TAG, "Projection park-stop monitor started")
            while (isActive) {
                val now = android.os.SystemClock.elapsedRealtime()
                val loc = try {
                    LocationHelper.getCurrentLocation(appContext)
                } catch (e: Exception) {
                    Log.w(TAG, "Projection location failed", e)
                    null
                }
                if (loc != null) {
                    settingsManager.saveLastKnownLocation(loc.latitude, loc.longitude)
                }
                val speed = if (loc != null && loc.hasSpeed()) loc.speed else null
                when (tracker.onSample(speed, now)) {
                    InSessionStopTracker.Result.Continue -> Unit
                    InSessionStopTracker.Result.Confirmed -> {
                        val settings = settingsManager.settings.value
                        val lat = loc?.latitude ?: settings.lastKnownLat
                        val lon = loc?.longitude ?: settings.lastKnownLon
                        if (lat != null && lon != null && !shouldSkipSuggestion(activeVehicleId(settings))) {
                            onParkConfirmed(
                                ParkCandidate(
                                    vehicleId = activeVehicleId(settings),
                                    latitude = lat,
                                    longitude = lon,
                                    createdAtEpochMs = System.currentTimeMillis(),
                                )
                            )
                        }
                    }
                }
                delay(POLL_MS)
            }
            Log.i(TAG, "Projection park-stop monitor stopped")
        }
    }

    fun stopProjectionMonitoring() {
        projectionJob?.cancel()
        projectionJob = null
    }

    fun startInSessionMonitoring() {
        ParkStopMonitorService.start(appContext)
    }

    fun stopInSessionMonitoring() {
        ParkStopMonitorService.stop(appContext)
    }

    /**
     * Fallback after AA disconnect or Gaston session destroy when a stop was not retained yet.
     *
     * @param sessionStartedAtElapsedMs [android.os.SystemClock.elapsedRealtime] when projection/session started
     */
    fun startIfEligible(
        sessionStartedAtElapsedMs: Long,
        minSessionMs: Long = MIN_SESSION_MS_BEFORE_PARK_SUGGEST,
        recentlySavedParkMs: Long = RECENTLY_SAVED_PARK_MS,
    ) {
        if (sessionStartedAtElapsedMs == 0L) return
        val sessionElapsed = android.os.SystemClock.elapsedRealtime() - sessionStartedAtElapsedMs
        if (sessionElapsed < minSessionMs) return

        val settings = settingsManager.settings.value
        val activeId = activeVehicleId(settings)
        if (shouldSkipSuggestion(activeId, recentlySavedParkMs)) {
            Log.d(TAG, "Post-session skipped: park already retained/suggested/saved")
            return
        }

        val lat = settings.lastKnownLat ?: return
        val lon = settings.lastKnownLon ?: return

        val candidate = ParkCandidate(
            vehicleId = activeId,
            latitude = lat,
            longitude = lon,
            createdAtEpochMs = System.currentTimeMillis(),
        )

        postSessionJob?.cancel()
        walkAwayMonitor.stop()

        val gateStart = android.os.SystemClock.elapsedRealtime()
        val tracker = PostDestroyDriveAbortTracker(startElapsedMs = gateStart)
        postSessionJob = scope.launch {
            while (isActive) {
                val now = android.os.SystemClock.elapsedRealtime()
                val loc = try {
                    LocationHelper.getCurrentLocation(appContext)
                } catch (e: Exception) {
                    Log.w(TAG, "Post-destroy location failed", e)
                    null
                }
                val speed = if (loc != null && loc.hasSpeed()) loc.speed else null
                when (tracker.onSample(speed, now)) {
                    PostDestroyDriveAbortTracker.Result.Continue -> delay(POLL_MS)
                    PostDestroyDriveAbortTracker.Result.Aborted -> {
                        Log.d(TAG, "Post-destroy park suggest aborted (still moving or timeout)")
                        return@launch
                    }
                    PostDestroyDriveAbortTracker.Result.Confirmed -> {
                        if (!shouldSkipSuggestion(activeId, recentlySavedParkMs)) {
                            onParkConfirmed(candidate)
                        }
                        return@launch
                    }
                }
            }
        }
    }

    private fun shouldSkipSuggestion(
        vehicleId: String,
        recentlySavedParkMs: Long = RECENTLY_SAVED_PARK_MS,
    ): Boolean {
        val candidate = candidateStore.get()
        if (candidate != null && (candidate.aaSuggestionShown || candidate.savedFromAa)) return true
        val existing = settingsManager.settings.value.parkedPositionFor(vehicleId) ?: return false
        return System.currentTimeMillis() - existing.savedAtEpochMs < recentlySavedParkMs
    }

    private fun activeVehicleId(settings: fr.geoking.gaston.AppSettings): String =
        settings.activeVehicleId.ifBlank { settings.vehicles.firstOrNull()?.id.orEmpty() }
            .ifBlank { "default" }

    private fun onParkConfirmed(candidate: ParkCandidate) {
        candidateStore.save(candidate.copy(aaSuggestionShown = true))
        val settings = settingsManager.settings.value
        val label = settings.vehicles.firstOrNull { it.id == candidate.vehicleId }?.displayLabel()?.ifBlank { null }
            ?: settings.activeVehicle()?.displayLabel()?.ifBlank { null }
            ?: listOf(settings.vehicleBrand, settings.vehicleModel)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { null }
        try {
            notificationHelper.showRememberParkedCarSuggestion(
                vehicleLabel = label,
                vehicleId = candidate.vehicleId,
                latitude = candidate.latitude,
                longitude = candidate.longitude,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to show remember-parked suggestion", e)
        }
        try {
            audio.speak(appContext.getString(R.string.notification_remember_parked_tts))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to speak park suggestion", e)
        }
        walkAwayMonitor.start(candidate)
    }

    companion object {
        private const val TAG = "AaPostSessionPark"
        private const val POLL_MS = 2_000L
        const val MIN_SESSION_MS_BEFORE_PARK_SUGGEST = 3 * 60 * 1000L
        const val RECENTLY_SAVED_PARK_MS = 15 * 60 * 1000L
    }
}
