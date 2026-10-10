package fr.geoking.gaston.parked

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import fr.geoking.gaston.MainActivity
import fr.geoking.gaston.R
import org.koin.core.context.GlobalContext

/**
 * Foreground location service that keeps the process alive while Android Auto / car mode
 * so park-stop detection can run even when another car app is in the foreground.
 */
class ParkStopMonitorService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        attachMonitor()
        return START_STICKY
    }

    override fun onDestroy() {
        detachMonitor()
        super.onDestroy()
    }

    private fun startAsForeground() {
        ensureChannel()
        val notification = buildOngoingNotification()
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    0
                },
            )
        } catch (e: Exception) {
            Log.w(TAG, "startForeground failed", e)
            // Fallback without typed API if the OEM rejects the type temporarily.
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildOngoingNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_poi_parking)
            .setContentTitle(getString(R.string.park_stop_monitor_title))
            .setContentText(getString(R.string.park_stop_monitor_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.park_stop_monitor_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.park_stop_monitor_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun attachMonitor() {
        try {
            GlobalContext.get().get<AaPostSessionParkSuggester>().attachFromService()
        } catch (e: Exception) {
            Log.w(TAG, "Koin not ready for park monitor", e)
        }
    }

    private fun detachMonitor() {
        try {
            GlobalContext.get().get<AaPostSessionParkSuggester>().detachFromService()
        } catch (e: Exception) {
            Log.w(TAG, "detachFromService failed", e)
        }
    }

    companion object {
        private const val TAG = "ParkStopMonitorSvc"
        private const val CHANNEL_ID = "gaston_park_stop_monitor"
        private const val NOTIFICATION_ID = 1010

        fun start(context: Context) {
            val app = context.applicationContext
            val intent = Intent(app, ParkStopMonitorService::class.java)
            try {
                ContextCompat.startForegroundService(app, intent)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to start park-stop FGS", e)
            }
        }

        fun stop(context: Context) {
            val app = context.applicationContext
            try {
                app.stopService(Intent(app, ParkStopMonitorService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Unable to stop park-stop FGS", e)
            }
        }
    }
}
