package fr.geoking.gaston.feature.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.car.app.notification.CarNotificationManager
import androidx.core.app.NotificationManagerCompat

/** Dismisses a connectivity alert when the driver marks it read (required for car HUN). */
class ConnectivityNotificationDismissReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_MARK_READ) return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (notificationId < 0) return
        CarNotificationManager.from(context).cancel(notificationId)
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    companion object {
        const val ACTION_MARK_READ =
            "fr.geoking.gaston.feature.notification.ACTION_MARK_CONNECTIVITY_READ"
        const val EXTRA_NOTIFICATION_ID =
            "fr.geoking.gaston.feature.notification.EXTRA_NOTIFICATION_ID"
    }
}
