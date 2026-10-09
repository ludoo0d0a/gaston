package fr.geoking.gaston.parked

import android.util.Log
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.feature.notification.NotificationHelper

/**
 * Shared save / ignore handlers for park-candidate alerts (phone notification actions + AA HUN).
 */
class ParkCandidateActions(
    private val settingsManager: SettingsManager,
    private val candidateStore: ParkCandidateStore,
    private val walkAwayMonitor: ParkWalkAwayMonitor,
    private val notificationHelper: NotificationHelper,
) {
    /**
     * Persists the frozen candidate (or [latitude]/[longitude] extras) as the parked pin.
     * @return true if a position was saved
     */
    fun save(
        vehicleId: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
    ): Boolean {
        val candidate = candidateStore.get()
        val id = vehicleId?.takeIf { it.isNotBlank() }
            ?: candidate?.vehicleId
            ?: settingsManager.settings.value.activeVehicleId.ifBlank { "default" }
        val coords = ParkCandidateSaveHelper.resolveSaveCoords(
            candidate = candidate,
            vehicleId = id,
            fallbackLat = latitude ?: candidate?.latitude,
            fallbackLon = longitude ?: candidate?.longitude,
        ) ?: run {
            Log.w(TAG, "Save parked: no candidate coords")
            return false
        }
        val (lat, lon) = coords
        settingsManager.saveParkedPosition(lat, lon, id)
        candidateStore.clear()
        walkAwayMonitor.stop()
        notificationHelper.cancelRememberParkedNotifications()
        Log.d(TAG, "Parked position saved for $id")
        return true
    }

    /** Drops the pending candidate and cancels related alerts. */
    fun ignore() {
        candidateStore.clear()
        walkAwayMonitor.stop()
        notificationHelper.cancelRememberParkedNotifications()
        Log.d(TAG, "Park candidate ignored")
    }

    companion object {
        private const val TAG = "ParkCandidateActions"
    }
}
