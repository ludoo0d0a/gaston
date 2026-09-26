package fr.geoking.gaston.feature.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import fr.geoking.gaston.SettingsManager
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

object LocationHelper {
    private const val TAG = "LocationHelper"
    private const val FRESH_AGE_MS = 300_000L // 5 minutes

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(
        context: Context,
        timeoutMs: Long = 3000L,
        priority: Int = Priority.PRIORITY_HIGH_ACCURACY
    ): Location? {
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)

        // 1. Try last location first (instant if available)
        val lastLocation = try {
            fusedClient.lastLocation.await()
        } catch (e: Exception) {
            Log.w(TAG, "lastLocation failed", e)
            null
        }
        if (lastLocation != null && (System.currentTimeMillis() - lastLocation.time) < FRESH_AGE_MS) {
            Log.d(TAG, "Using fresh lastLocation from FusedLocationProviderClient")
            return lastLocation
        }

        // 2. Request a fresh location with timeout
        Log.d(TAG, "Requesting fresh location (priority=$priority, timeout=${timeoutMs}ms)")
        val cts = CancellationTokenSource()
        val fresh = withTimeoutOrNull(timeoutMs) {
            try {
                fusedClient.getCurrentLocation(priority, cts.token).await()
            } finally {
                cts.cancel()
            }
        }
        if (fresh != null) {
            Log.d(TAG, "Got fresh location from getCurrentLocation")
            return fresh
        }

        // 3. Fallback to last location even if stale
        Log.d(TAG, "Fresh update timed out or failed, using last known location")
        return lastLocation
    }

    /**
     * Tries to get the current location. If it fails, falls back to the stored location in [SettingsManager].
     * Updates [SettingsManager] with the new location if a fresh GPS fix is obtained.
     */
    suspend fun getInitialLocation(context: Context, settingsManager: SettingsManager): Pair<Double, Double> {
        val current = getCurrentLocation(context)
        if (current != null) {
            settingsManager.saveLastKnownLocation(current.latitude, current.longitude)
            return current.latitude to current.longitude
        }

        val settings = settingsManager.settings.value
        if (settings.lastKnownLat != null && settings.lastKnownLon != null) {
            Log.d(TAG, "Using stored location from settings: ${settings.lastKnownLat}, ${settings.lastKnownLon}")
            return settings.lastKnownLat to settings.lastKnownLon
        }

        // Final hardcoded fallback: Paris
        Log.d(TAG, "No GPS and no stored location, falling back to Paris")
        return 48.8566 to 2.3522
    }

    /**
     * Emits continuous [Location] updates via a Kotlin [Flow].
     */
    @SuppressLint("MissingPermission")
    fun getLocationUpdates(context: Context, intervalMs: Long = 2000L): Flow<Location> = callbackFlow {
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(1000L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }

        try {
            fusedClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request location updates", e)
        }

        awaitClose {
            try {
                fusedClient.removeLocationUpdates(callback)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove location updates", e)
            }
        }
    }
}
