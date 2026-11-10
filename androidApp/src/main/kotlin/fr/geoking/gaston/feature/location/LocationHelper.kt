package fr.geoking.gaston.feature.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import fr.geoking.gaston.SettingsManager
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

object LocationHelper {
    private const val TAG = "LocationHelper"
    private const val FRESH_AGE_MS = 300_000L // 5 minutes

    /**
     * Once fused LocationServices fails with API_NOT_CONNECTED / SERVICE_INVALID on a device,
     * keep using [LocationManager] for the rest of the process — Play Services availability
     * checks can still return SUCCESS while LocationServices.API is unusable.
     */
    private val fusedLocationUsable = AtomicBoolean(true)

    fun markFusedLocationUnavailable() {
        if (fusedLocationUsable.getAndSet(false)) {
            Log.w(TAG, "Marked Fused Location unavailable for this process")
        }
    }

    private fun canUseFusedLocation(context: Context): Boolean {
        if (!fusedLocationUsable.get()) return false
        return try {
            GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        } catch (e: Exception) {
            Log.w(TAG, "Play Services availability check failed", e)
            false
        }
    }

    private fun noteFusedFailure(e: Throwable) {
        if (isLocationServicesUnavailable(e)) {
            markFusedLocationUnavailable()
        }
    }

    /** True for ApiException 17 / messages about LocationServices.API not available. */
    fun isLocationServicesUnavailable(throwable: Throwable): Boolean {
        val chain = generateSequence(throwable) { it.cause }
        for (t in chain) {
            if (t is ApiException) {
                val code = t.statusCode
                val msg = t.message.orEmpty()
                if (code == CommonStatusCodes.API_NOT_CONNECTED ||
                    msg.contains("LocationServices", ignoreCase = true)
                ) {
                    return true
                }
            }
            val msg = t.message.orEmpty()
            if (msg.contains("LocationServices.API is not available", ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    @SuppressLint("MissingPermission")
    private fun getLastKnownFromLocationManager(context: Context): Location? {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER,
            ).mapNotNull { provider ->
                try {
                    if (lm.isProviderEnabled(provider)) lm.getLastKnownLocation(provider) else null
                } catch (e: Exception) {
                    Log.w(TAG, "getLastKnownLocation($provider) failed", e)
                    null
                }
            }.maxByOrNull { it.time }
        } catch (e: Exception) {
            Log.w(TAG, "LocationManager lastKnown failed", e)
            null
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(
        context: Context,
        timeoutMs: Long = 3000L,
        priority: Int = Priority.PRIORITY_HIGH_ACCURACY
    ): Location? {
        if (!canUseFusedLocation(context)) {
            Log.w(TAG, "LocationServices unavailable; falling back to LocationManager")
            return getLastKnownFromLocationManager(context)
        }

        return try {
            getCurrentLocationFromFused(context, timeoutMs, priority)
                ?: getLastKnownFromLocationManager(context)
        } catch (e: Exception) {
            noteFusedFailure(e)
            Log.w(TAG, "Fused location failed; falling back to LocationManager", e)
            getLastKnownFromLocationManager(context)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocationFromFused(
        context: Context,
        timeoutMs: Long,
        priority: Int
    ): Location? {
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)

        // 1. Try last location first (instant if available)
        val lastLocation = try {
            fusedClient.lastLocation.await()
        } catch (e: Exception) {
            noteFusedFailure(e)
            Log.w(TAG, "lastLocation failed", e)
            if (isLocationServicesUnavailable(e)) return null
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
            } catch (e: Exception) {
                noteFusedFailure(e)
                Log.w(TAG, "getCurrentLocation failed", e)
                null
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
        if (!canUseFusedLocation(context)) {
            collectFromLocationManager(context, intervalMs)
            return@callbackFlow
        }

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
            // Await the Task so API_NOT_CONNECTED / SERVICE_INVALID is handled here,
            // not left as an unobserved failure on GoogleApiHandler.
            fusedClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper()).await()
        } catch (e: Exception) {
            noteFusedFailure(e)
            Log.w(TAG, "Failed to request fused location updates; falling back to LocationManager", e)
            collectFromLocationManager(context, intervalMs)
            return@callbackFlow
        }

        awaitClose {
            try {
                fusedClient.removeLocationUpdates(callback)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove location updates", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun ProducerScope<Location>.collectFromLocationManager(
        context: Context,
        intervalMs: Long,
    ) {
        Log.w(TAG, "Using LocationManager updates")
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        var registered = false
        for (provider in providers) {
            try {
                if (lm.isProviderEnabled(provider)) {
                    lm.requestLocationUpdates(provider, intervalMs, 0f, listener, Looper.getMainLooper())
                    registered = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to request LocationManager updates from $provider", e)
            }
        }
        if (!registered) {
            close()
            return
        }
        awaitClose {
            try {
                lm.removeUpdates(listener)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove LocationManager updates", e)
            }
        }
    }
}
