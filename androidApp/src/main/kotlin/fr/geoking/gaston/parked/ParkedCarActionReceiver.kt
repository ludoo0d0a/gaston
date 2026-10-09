package fr.geoking.gaston.parked

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import fr.geoking.gaston.ParkedCarIntents
import org.koin.core.context.GlobalContext

/**
 * Handles Save / Ignore actions from the phone park-suggestion notification.
 */
class ParkedCarActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val actions = try {
            GlobalContext.get().get<ParkCandidateActions>()
        } catch (e: Exception) {
            Log.w(TAG, "Koin not ready for park action", e)
            return
        }
        when (intent.action) {
            ParkedCarIntents.ACTION_SAVE_CANDIDATE -> {
                val vehicleId = intent.getStringExtra(ParkedCarIntents.EXTRA_VEHICLE_ID)
                val lat = intent.getDoubleExtra(ParkedCarIntents.EXTRA_LATITUDE, Double.NaN)
                val lon = intent.getDoubleExtra(ParkedCarIntents.EXTRA_LONGITUDE, Double.NaN)
                actions.save(
                    vehicleId = vehicleId,
                    latitude = lat.takeUnless { it.isNaN() },
                    longitude = lon.takeUnless { it.isNaN() },
                )
            }
            ParkedCarIntents.ACTION_IGNORE_CANDIDATE -> actions.ignore()
            else -> Log.d(TAG, "Unhandled action ${intent.action}")
        }
    }

    companion object {
        private const val TAG = "ParkedCarActionReceiver"
    }
}
