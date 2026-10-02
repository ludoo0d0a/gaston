package fr.geoking.gaston.feature.maintenance

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import fr.geoking.gaston.R

/**
 * Posts phone-only notifications when service intervals are due.
 */
class MaintenanceReminderNotifier(
    private val context: Context,
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannel()
    }

    fun notifyDue(reminders: List<MaintenanceReminderEvaluator.DueReminder>) {
        if (reminders.isEmpty()) return
        if (!canPost()) return
        reminders.forEachIndexed { index, due ->
            val kindLabel = serviceKindLabel(due.interval.serviceKind)
            val title = context.getString(R.string.maintenance_reminder_title)
            val message = context.getString(R.string.maintenance_reminder_message, kindLabel)
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
            notificationManager.notify(NOTIFICATION_ID_BASE + index, builder.build())
        }
    }

    private fun serviceKindLabel(kind: ServiceKind): String {
        val res = when (kind) {
            ServiceKind.OIL_CHANGE -> R.string.maintenance_service_oil
            ServiceKind.TIRES -> R.string.maintenance_service_tires
            ServiceKind.BRAKES -> R.string.maintenance_service_brakes
            ServiceKind.INSPECTION -> R.string.maintenance_service_inspection
            ServiceKind.REVISION -> R.string.maintenance_service_revision
            ServiceKind.OTHER -> R.string.maintenance_service_other
        }
        return context.getString(res)
    }

    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.maintenance_reminder_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "gaston_maintenance"
        private const val NOTIFICATION_ID_BASE = 2100
    }
}
