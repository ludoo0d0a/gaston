package fr.geoking.gaston.feature.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import fr.geoking.gaston.R

class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val CHANNEL_ID = "gaston_alerts"
        private const val CHANNEL_NAME = "Gaston Alerts"
        private const val BORDER_NOTIFICATION_ID = 2001
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for Gaston alerts like border crossings"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showBorderCrossingNotification(countryName: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_map) // Using existing ic_map
            .setContentTitle("Welcome to $countryName")
            .setContentText("You have entered a new country.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(BORDER_NOTIFICATION_ID, notification)
    }
}
