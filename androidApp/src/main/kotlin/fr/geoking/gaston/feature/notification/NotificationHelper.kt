package fr.geoking.gaston.feature.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.car.app.notification.CarAppExtender
import androidx.car.app.notification.CarNotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import fr.geoking.gaston.R

class NotificationHelper(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "gaston_alerts"
        private const val NOTIFICATION_ID_BORDER = 1001
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.dashboard_network)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showBorderCrossingNotification(countryName: String) {
        if (!canPostNotifications()) return

        val title = context.getString(R.string.notification_border_crossing_title)
        val message = context.getString(R.string.notification_border_crossing_message, countryName)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .extend(
                CarAppExtender.Builder()
                    .setImportance(NotificationManager.IMPORTANCE_HIGH)
                    .build()
            )

        val notification = builder.build()
        notificationManager.notify(NOTIFICATION_ID_BORDER, notification)
        CarNotificationManager.from(context).notify(NOTIFICATION_ID_BORDER, builder)
    }

    fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
