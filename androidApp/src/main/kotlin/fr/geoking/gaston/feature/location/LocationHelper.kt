package fr.geoking.gaston.feature.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

object LocationHelper {
    private const val TAG = "LocationHelper"
    private const val FRESH_AGE_MS = 300_000L // 5 minutes

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context, timeoutMs: Long = 3000L): Location? {
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
        Log.d(TAG, "Requesting fresh location (timeout ${timeoutMs}ms)")
        val cts = CancellationTokenSource()
        val fresh = withTimeoutOrNull(timeoutMs) {
            try {
                fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
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
}
