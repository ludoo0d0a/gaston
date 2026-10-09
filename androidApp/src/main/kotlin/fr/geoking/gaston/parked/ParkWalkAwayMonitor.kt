package fr.geoking.gaston.parked

import android.content.Context
import android.util.Log
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.activeVehicle
import fr.geoking.gaston.feature.location.LocationHelper
import fr.geoking.gaston.feature.notification.NotificationHelper
import fr.geoking.gaston.parkedPositionFor
import fr.geoking.gaston.shared.location.haversineKm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * After a park candidate is created, watch phone GPS until the user walks away
 * from the frozen car position, then post a phone-only notification (case 1 or 2).
 */
class ParkWalkAwayMonitor(
    context: Context,
    private val settingsManager: SettingsManager,
    private val notificationHelper: NotificationHelper,
    private val candidateStore: ParkCandidateStore,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    fun start(candidate: ParkCandidate) {
        job?.cancel()
        val tracker = WalkAwayDistanceTracker(candidate.latitude, candidate.longitude)
        val startedAt = android.os.SystemClock.elapsedRealtime()
        job = scope.launch {
            while (isActive) {
                val elapsed = android.os.SystemClock.elapsedRealtime() - startedAt
                if (elapsed >= MONITOR_TIMEOUT_MS) {
                    Log.d(TAG, "Walk-away monitor timed out")
                    candidateStore.clear()
                    return@launch
                }
                val loc = try {
                    LocationHelper.getCurrentLocation(appContext)
                } catch (e: Exception) {
                    Log.w(TAG, "Walk-away location failed", e)
                    null
                }
                if (loc != null &&
                    tracker.onSample(loc.latitude, loc.longitude) == WalkAwayDistanceTracker.Result.Triggered
                ) {
                    onWalkedAway(candidate)
                    return@launch
                }
                delay(POLL_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun onWalkedAway(origin: ParkCandidate) {
        val current = candidateStore.get() ?: origin
        val settings = settingsManager.settings.value
        val parked = settings.parkedPositionFor(current.vehicleId)
        val alreadySaved = current.savedFromAa ||
            (parked != null && roughlySameSpot(parked.latitude, parked.longitude, current.latitude, current.longitude))

        val label = settings.vehicles.firstOrNull { it.id == current.vehicleId }?.displayLabel()?.ifBlank { null }
            ?: settings.activeVehicle()?.displayLabel()?.ifBlank { null }
            ?: listOf(settings.vehicleBrand, settings.vehicleModel)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { null }

        try {
            if (alreadySaved) {
                notificationHelper.showPhoneParkedPositionConfirmed(label)
                candidateStore.clear()
            } else {
                // Keep candidate so Save/Ignore actions and the parking screen map can use it.
                notificationHelper.showPhoneRememberParkedSuggestion(
                    vehicleLabel = label,
                    vehicleId = current.vehicleId,
                    latitude = current.latitude,
                    longitude = current.longitude,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to show walk-away parked notification", e)
            if (alreadySaved) candidateStore.clear()
        }
    }

    private fun roughlySameSpot(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Boolean {
        if (abs(lat1 - lat2) < 1e-5 && abs(lon1 - lon2) < 1e-5) return true
        return haversineKm(lat1, lon1, lat2, lon2) * 1000.0 <= SAME_SPOT_METERS
    }

    companion object {
        private const val TAG = "ParkWalkAwayMonitor"
        private const val POLL_MS = 8_000L
        private const val MONITOR_TIMEOUT_MS = 15 * 60 * 1000L
        private const val SAME_SPOT_METERS = 25.0
    }
}
