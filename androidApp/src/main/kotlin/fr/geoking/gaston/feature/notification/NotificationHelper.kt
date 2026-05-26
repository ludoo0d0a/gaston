package fr.geoking.gaston.feature.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.car.app.notification.CarAppExtender
import androidx.car.app.notification.CarNotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import fr.geoking.gaston.R

class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val carNotificationManager = CarNotificationManager.from(context)

    companion object {
        private const val TAG = "NotificationHelper"
        private const val CHANNEL_ID = "gaston_connectivity_alerts"
        private const val CHANNEL_NAME = "Gaston connectivity alerts"
        private const val BORDER_NOTIFICATION_ID_BASE = 2001
        private const val BORDER_NOTIFICATION_ID_MAX = 2099
    }

    private val notificationIdLock = Any()
    private var nextNotificationId = BORDER_NOTIFICATION_ID_BASE

    init {
        createNotificationChannel()
    }

    fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Border crossing and connectivity alerts (phone and Android Auto)"
        }
        notificationManager.createNotificationChannel(channel)
    }

    @SuppressLint("NotificationPermission") // Gated via canPostNotifications().
    fun showConnectivityNotification(title: String, message: String) {
        if (!canPostNotifications()) {
            android.util.Log.w(TAG, "Skipping connectivity notification: POST_NOTIFICATIONS denied")
            return
        }

        val notificationId = allocateNotificationId()
        val appName = context.getString(R.string.app_name)
        val person = Person.Builder().setName(appName).build()
        val messagingStyle = NotificationCompat.MessagingStyle(person)
            .setConversationTitle(title)
            .addMessage(message, System.currentTimeMillis(), person)

        val carExtender = CarAppExtender.Builder()
            .setContentTitle(title)
            .setContentText(message)
            // Projected Android Auto: overrides car-screen importance for HUN.
            .setImportance(NotificationManagerCompat.IMPORTANCE_HIGH)
            // Android Automotive OS: use the high-importance channel on the car screen.
            .setChannelId(CHANNEL_ID)
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_map)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(messagingStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
            .addAction(buildMarkAsReadAction(notificationId))
            .extend(carExtender)

        carNotificationManager.notify(notificationId, notification)
    }

    private fun buildMarkAsReadAction(notificationId: Int): NotificationCompat.Action {
        val intent = Intent(context, ConnectivityNotificationDismissReceiver::class.java).apply {
            action = ConnectivityNotificationDismissReceiver.ACTION_MARK_READ
            putExtra(
                ConnectivityNotificationDismissReceiver.EXTRA_NOTIFICATION_ID,
                notificationId,
            )
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_notifications,
            context.getString(R.string.notification_mark_read),
            pendingIntent,
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()
    }

    private fun allocateNotificationId(): Int = synchronized(notificationIdLock) {
        val id = nextNotificationId
        nextNotificationId = if (nextNotificationId >= BORDER_NOTIFICATION_ID_MAX) {
            BORDER_NOTIFICATION_ID_BASE
        } else {
            nextNotificationId + 1
        }
        id
    }
}
