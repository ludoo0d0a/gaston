package fr.geoking.gaston.parked

import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Cold-start / keep-alive when the device enters car mode (Android Auto / dock).
 * Actual stop detection still keys off projection + GPS via [AaPostSessionParkSuggester].
 */
class CarModeParkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            UiModeManager.ACTION_ENTER_CAR_MODE -> {
                Log.i(TAG, "ENTER_CAR_MODE → start park-stop FGS")
                ParkStopMonitorService.start(context)
            }
            UiModeManager.ACTION_EXIT_CAR_MODE -> {
                // Do not stop here: CarConnection may still report projection briefly,
                // and AaPostSessionParkSuggester owns the disconnect fallback.
                Log.d(TAG, "EXIT_CAR_MODE (FGS stop deferred to CarConnection)")
            }
        }
    }

    companion object {
        private const val TAG = "CarModeParkReceiver"
    }
}
