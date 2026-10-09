package fr.geoking.gaston.parked

import android.content.Context
import android.util.Log
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
 * Park suggestion triggers:
 * 1. **In-session**: after vehicle-like speed then ~10s stop during Android Auto → propose Save.
 * 2. **Post-session** (fallback): on AA destroy, if nothing was retained yet → same gate + propose.
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
    private var inSessionJob: Job? = null
    private var postSessionJob: Job? = null

    /** Start GPS polling for stop detection while the AA session is alive. */
    fun startInSessionMonitoring() {
        inSessionJob?.cancel()
        postSessionJob?.cancel()
        val tracker = InSessionStopTracker()
        inSessionJob = scope.launch {
            while (isActive) {
                val now = android.os.SystemClock.elapsedRealtime()
                val loc = try {
                    LocationHelper.getCurrentLocation(appContext)
                } catch (e: Exception) {
                    Log.w(TAG, "In-session location failed", e)
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
                        if (lat != null && lon != null && !hasRetainedParkSuggestion()) {
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
        }
    }

    fun stopInSessionMonitoring() {
        inSessionJob?.cancel()
        inSessionJob = null
    }

    /**
     * Fallback after Android Auto session destroy when in-session did not retain a suggestion.
     *
     * @param sessionStartedAtElapsedMs [android.os.SystemClock.elapsedRealtime] when the AA session started
     */
    fun startIfEligible(
        sessionStartedAtElapsedMs: Long,
        minSessionMs: Long = MIN_SESSION_MS_BEFORE_PARK_SUGGEST,
        recentlySavedParkMs: Long = RECENTLY_SAVED_PARK_MS,
    ) {
        stopInSessionMonitoring()

        if (sessionStartedAtElapsedMs == 0L) return
        val sessionElapsed = android.os.SystemClock.elapsedRealtime() - sessionStartedAtElapsedMs
        if (sessionElapsed < minSessionMs) return

        val settings = settingsManager.settings.value
        val activeId = activeVehicleId(settings)
        if (shouldSkipPostSessionFallback(activeId, recentlySavedParkMs)) {
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
                        if (!hasRetainedParkSuggestion()) {
                            onParkConfirmed(candidate)
                        }
                        return@launch
                    }
                }
            }
        }
    }

    /** True when a suggestion is already frozen for this candidate. */
    private fun hasRetainedParkSuggestion(): Boolean {
        val candidate = candidateStore.get() ?: return false
        return candidate.aaSuggestionShown || candidate.savedFromAa
    }

    /**
     * Skip post-session if in-session already proposed, or the pin was saved recently
     * (candidate may already be cleared after Save).
     */
    private fun shouldSkipPostSessionFallback(
        vehicleId: String,
        recentlySavedParkMs: Long,
    ): Boolean {
        if (hasRetainedParkSuggestion()) return true
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
        walkAwayMonitor.start(candidate)
    }

    companion object {
        private const val TAG = "AaPostSessionPark"
        private const val POLL_MS = 2_000L
        const val MIN_SESSION_MS_BEFORE_PARK_SUGGEST = 3 * 60 * 1000L
        const val RECENTLY_SAVED_PARK_MS = 15 * 60 * 1000L
    }
}
