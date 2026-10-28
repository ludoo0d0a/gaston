package fr.geoking.gaston.parked

import android.app.UiModeManager
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import fr.geoking.gaston.SettingsManager
import org.koin.core.context.GlobalContext

/**
 * Background receiver for vehicle connection events (Bluetooth car kit, Android Auto car mode, boot).
 * Wakes up Koin and [AaPostSessionParkSuggester] when auto-park on AA disconnect is enabled.
 */
class AaCarModeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.d(TAG, "onReceive: action=$action")

        val koin = GlobalContext.getOrNull() ?: return
        val settingsManager = koin.getOrNull<SettingsManager>() ?: return
        val settings = settingsManager.settings.value

        if (!settings.autoParkOnAaDisconnectEnabled) {
            Log.d(TAG, "autoParkOnAaDisconnectEnabled is false; skipping background wakeup")
            return
        }

        when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED,
            BluetoothDevice.ACTION_ACL_DISCONNECTED,
            UiModeManager.ACTION_ENTER_CAR_MODE,
            UiModeManager.ACTION_EXIT_CAR_MODE,
            Intent.ACTION_BOOT_COMPLETED -> {
                // Accessing AaPostSessionParkSuggester ensures its instance exists and listens to CarConnection
                val suggester = koin.getOrNull<AaPostSessionParkSuggester>()
                Log.d(TAG, "AaPostSessionParkSuggester background wakeup OK: ${suggester != null}")
            }
        }
    }

    companion object {
        private const val TAG = "AaCarModeReceiver"
    }
}
