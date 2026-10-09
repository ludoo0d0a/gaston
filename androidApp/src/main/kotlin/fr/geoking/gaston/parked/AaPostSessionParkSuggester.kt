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
 * After Android Auto session destroy: wait 30s without vehicle-like GPS speed,
 * then show the AA remember-parked suggestion and start walk-away monitoring.
 * Fully separate from the danger-zone / radar loop.
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
    private var job: Job? = null

    /**
     * @param sessionStartedAtElapsedMs [android.os.SystemClock.elapsedRealtime] when the AA session started
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
        val lat = settings.lastKnownLat ?: return
        val lon = settings.lastKnownLon ?: return

        val activeId = settings.activeVehicleId.ifBlank { settings.vehicles.firstOrNull()?.id.orEmpty() }
            .ifBlank { "default" }
        val existing = settings.parkedPositionFor(activeId)
        if (existing != null &&
            System.currentTimeMillis() - existing.savedAtEpochMs < recentlySavedParkMs
        ) {
            return
        }

        val candidate = ParkCandidate(
            vehicleId = activeId,
            latitude = lat,
            longitude = lon,
            createdAtEpochMs = System.currentTimeMillis(),
        )

        job?.cancel()
        walkAwayMonitor.stop()

        val gateStart = android.os.SystemClock.elapsedRealtime()
        val tracker = PostDestroyDriveAbortTracker(startElapsedMs = gateStart)
        job = scope.launch {
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
                        onParkConfirmed(candidate)
                        return@launch
                    }
                }
            }
        }
    }

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
