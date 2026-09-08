package fr.geoking.gaston.feature.location

import android.Manifest
import androidx.test.core.app.ApplicationProvider
import fr.geoking.gaston.SettingsManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowApplication

/**
 * LocationHelper.getCurrentLocation is @SuppressLint("MissingPermission") with no
 * hasLocationPermission() guard before calling Fused Location — a guard added in commit 5f520de was
 * reverted by 43fee43 and is confirmed absent from HEAD today.
 *
 * IMPORTANT: this cannot be turned into a test that fails without the guard. Verified empirically:
 * under Robolectric there is no shadow for com.google.android.gms:play-services-location, so
 * FusedLocationProviderClient never reaches a real permission check either way — getCurrentLocation
 * returns null cleanly regardless of whether permission is granted or denied. Reproducing the real
 * SecurityException needs a device/emulator with real Play Services, which is out of scope for this
 * JVM/Robolectric suite. These tests only guard the fallback behavior that IS observable here: no
 * crash, and a sane fallback location when nothing else is available.
 */
@RunWith(RobolectricTestRunner::class)
class LocationHelperPermissionTest {

    private fun denyLocationPermissions(context: android.content.Context) {
        val shadowApplication: ShadowApplication = shadowOf(context as android.app.Application)
        shadowApplication.denyPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }

    @Test
    fun getCurrentLocationDoesNotThrowWhenPermissionIsDenied() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        denyLocationPermissions(context)

        runBlocking {
            LocationHelper.getCurrentLocation(context)
        }
    }

    @Test
    fun getInitialLocationFallsBackToParisWithNoGpsAndNoStoredLocation() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        denyLocationPermissions(context)
        val settingsManager = SettingsManager(context)

        val (lat, lon) = runBlocking { LocationHelper.getInitialLocation(context, settingsManager) }

        assertEquals(48.8566, lat, 0.0001)
        assertEquals(2.3522, lon, 0.0001)
    }
}
