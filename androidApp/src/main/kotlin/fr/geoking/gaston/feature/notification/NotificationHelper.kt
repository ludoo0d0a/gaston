package fr.geoking.gaston.feature.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.car.app.notification.CarAppExtender
import androidx.car.app.notification.CarNotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import fr.geoking.gaston.R

class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val carNotificationManager = CarNotificationManager.from(context)

    companion object {
        private const val CHANNEL_ID = "gaston_connectivity_alerts"
        private const val CHANNEL_NAME = "Gaston connectivity alerts"
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
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Border crossing and connectivity alerts (phone and Android Auto)"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("NotificationPermission") // Gated below on Android 13+.
    fun showConnectivityNotification(title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted =
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val carExtender = CarAppExtender.Builder()
            .setContentTitle(title)
            .setContentText(message)
            // Android Auto: IMPORTANCE_HIGH shows a heads-up notification (HUN) on the car screen.
            .setImportance(NotificationManagerCompat.IMPORTANCE_HIGH)
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_map)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Eligible for car HUN on Automotive OS; also valid for projected Android Auto.
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .extend(carExtender)

        // Car App Library: must post via CarNotificationManager when using CarAppExtender.
        carNotificationManager.notify(BORDER_NOTIFICATION_ID, notification)
    }
}
