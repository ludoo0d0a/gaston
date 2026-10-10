package fr.geoking.gaston.feature.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.car.app.notification.CarAppExtender
import androidx.car.app.notification.CarNotificationManager
import androidx.car.app.notification.CarPendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import fr.geoking.gaston.MainActivity
import fr.geoking.gaston.ParkedCarIntents
import fr.geoking.gaston.R
import fr.geoking.gaston.auto.VoiceAppService

open class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "gaston_alerts"
        private const val NOTIFICATION_ID_BORDER = 1001
        private const val NOTIFICATION_ID_UPDATE = 1002
        private const val NOTIFICATION_ID_DANGER_ZONE = 1003
        private const val NOTIFICATION_ID_NEAR_RADAR = 1004
        private const val NOTIFICATION_ID_REMEMBER_PARKED = 1005
        private const val NOTIFICATION_ID_PHONE_PARKED_CONFIRMED = 1006
        private const val NOTIFICATION_ID_PHONE_REMEMBER_PARKED = 1007
        private const val REQ_PHONE_SAVE_AA = 1105
        private const val REQ_PHONE_IGNORE_AA = 1106
        private const val REQ_PHONE_SAVE_WALK = 1107
        private const val REQ_PHONE_IGNORE_WALK = 1108
        private const val REQ_CAR_SAVE = 1205
        /** Suppress duplicate HUNs when phone + AA alert loops both fire. */
        private const val DEDUPE_WINDOW_MS = 8_000L
        /** Avoid spamming the park suggestion when AA reconnects briefly. */
        private const val REMEMBER_PARKED_DEDUPE_MS = 30 * 60 * 1000L
        /** Phone + AA temporary danger-zone HUNs auto-dismiss after this delay. */
        private const val TEMPORARY_NOTIFICATION_MS = 6_000L
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingCancelById = mutableMapOf<Int, Runnable>()

    private val lastPostedAtByKind = mutableMapOf<String, Pair<String, Long>>()

    init {
        createNotificationChannel()
    }

    @Synchronized
    private fun shouldSuppressDuplicate(
        kind: String,
        key: String,
        windowMs: Long = DEDUPE_WINDOW_MS,
    ): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        val prev = lastPostedAtByKind[kind]
        if (prev != null && prev.first == key && now - prev.second < windowMs) {
            return true
        }
        lastPostedAtByKind[kind] = key to now
        return false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.dashboard_network)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Phone + Android Auto HUN for a border crossing.
     * May warn about a required vignette, but never include shop URLs/actions —
     * those live in [InAppNotificationCenter] on the phone.
     */
    fun showBorderCrossingNotification(countryName: String, requiresVignette: Boolean = false) {
        if (!canPostNotifications()) return

        val title = context.getString(R.string.notification_border_crossing_title)
        val message = if (requiresVignette) {
            context.getString(R.string.notification_border_crossing_message_vignette, countryName)
        } else {
            context.getString(R.string.notification_border_crossing_message, countryName)
        }

        val phoneIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val phonePending = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_BORDER,
            phoneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val carIntent = Intent(context, VoiceAppService::class.java)
        val carPending = CarPendingIntent.getCarApp(
            context,
            NOTIFICATION_ID_BORDER,
            carIntent,
            PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(phonePending)
            .extend(
                CarAppExtender.Builder()
                    .setImportance(NotificationManager.IMPORTANCE_HIGH)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setSmallIcon(R.drawable.ic_notifications)
                    .setContentIntent(carPending)
                    .build()
            )

        val notification = builder.build()
        notificationManager.notify(NOTIFICATION_ID_BORDER, notification)
        CarNotificationManager.from(context).notify(NOTIFICATION_ID_BORDER, builder)
    }

    /**
     * Android Auto HUN for an available update.
     * Phone notification (tap → start update) is posted by
     * `fr.geoking.tools.inappupdate.InAppUpdateHelper`.
     */
    fun showUpdateAvailableCarNotification() {
        if (!canPostNotifications()) return

        val title = context.getString(R.string.update_available_title)
        val message = context.getString(R.string.update_available_message)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .extend(
                CarAppExtender.Builder()
                    .setImportance(NotificationManager.IMPORTANCE_HIGH)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setSmallIcon(R.drawable.ic_notifications)
                    .build()
            )

        CarNotificationManager.from(context).notify(NOTIFICATION_ID_UPDATE, builder)
    }

    @Deprecated(
        message = "Phone update notification is handled by geoking-tools InAppUpdateHelper",
        replaceWith = ReplaceWith("showUpdateAvailableCarNotification()"),
    )
    fun showUpdateAvailableNotification() = showUpdateAvailableCarNotification()

    /** Temporary phone notification + Android Auto HUN on danger-zone entry. */
    open fun showDangerZoneNotification(speedLimitKmH: Int?) {
        if (!canPostNotifications()) return
        val dedupeKey = "entry-${speedLimitKmH ?: 0}"
        if (shouldSuppressDuplicate("danger_zone", dedupeKey)) return

        val title = context.getString(R.string.notification_danger_zone_title)
        val message = if (speedLimitKmH != null && speedLimitKmH > 0) {
            context.getString(R.string.notification_danger_zone_message_speed, speedLimitKmH)
        } else {
            context.getString(R.string.notification_danger_zone_message)
        }

        postTemporaryCarHeadsUp(
            notificationId = NOTIFICATION_ID_DANGER_ZONE,
            title = title,
            message = message,
            smallIcon = R.drawable.ic_poi_radar,
        )
    }

    /**
     * Temporary HUN when within ~100 m of the zone center (after / with zone entry).
     * FR copy: "Radar proche %d km/h".
     */
    open fun showNearRadarNotification(speedLimitKmH: Int?) {
        if (!canPostNotifications()) return
        val dedupeKey = "near-${speedLimitKmH ?: 0}"
        if (shouldSuppressDuplicate("near_radar", dedupeKey)) return

        val title = context.getString(R.string.notification_near_radar_title)
        val message = if (speedLimitKmH != null && speedLimitKmH > 0) {
            context.getString(R.string.notification_near_radar_message, speedLimitKmH)
        } else {
            context.getString(R.string.notification_near_radar_message_generic)
        }

        postTemporaryCarHeadsUp(
            notificationId = NOTIFICATION_ID_NEAR_RADAR,
            title = title,
            message = message,
            smallIcon = R.drawable.ic_poi_radar,
        )
    }

    /**
     * Phone + Android Auto HUN suggesting to remember the parking spot
     * (typically when the AA session ends after a stop is confirmed).
     * Actions: Save / Ignore.
     */
    open fun showRememberParkedCarSuggestion(
        vehicleLabel: String?,
        vehicleId: String,
        latitude: Double,
        longitude: Double,
    ) {
        if (!canPostNotifications()) return
        if (shouldSuppressDuplicate("remember_parked", "suggest", REMEMBER_PARKED_DEDUPE_MS)) return

        val title = context.getString(R.string.notification_remember_parked_title)
        val message = if (!vehicleLabel.isNullOrBlank()) {
            context.getString(R.string.notification_remember_parked_message, vehicleLabel)
        } else {
            context.getString(R.string.notification_remember_parked_message_generic)
        }

        val phoneIntent = Intent(context, MainActivity::class.java).apply {
            action = ParkedCarIntents.ACTION_REMEMBER
            putExtra(ParkedCarIntents.EXTRA_VEHICLE_ID, vehicleId)
            putExtra(ParkedCarIntents.EXTRA_LATITUDE, latitude)
            putExtra(ParkedCarIntents.EXTRA_LONGITUDE, longitude)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val phonePending = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_REMEMBER_PARKED,
            phoneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val phoneSave = parkActionPendingBroadcast(
            requestCode = REQ_PHONE_SAVE_AA,
            action = ParkedCarIntents.ACTION_SAVE_CANDIDATE,
            vehicleId = vehicleId,
            latitude = latitude,
            longitude = longitude,
        )
        val phoneIgnore = parkActionPendingBroadcast(
            requestCode = REQ_PHONE_IGNORE_AA,
            action = ParkedCarIntents.ACTION_IGNORE_CANDIDATE,
            vehicleId = vehicleId,
            latitude = latitude,
            longitude = longitude,
        )

        // AA HUN: single primary action "Save" (phone keeps Save + Ignore).
        val carSave = CarPendingIntent.getCarApp(
            context,
            REQ_CAR_SAVE,
            Intent(ParkedCarIntents.ACTION_SAVE_CANDIDATE).apply {
                component = ComponentName(context, VoiceAppService::class.java)
                putExtra(ParkedCarIntents.EXTRA_VEHICLE_ID, vehicleId)
                putExtra(ParkedCarIntents.EXTRA_LATITUDE, latitude)
                putExtra(ParkedCarIntents.EXTRA_LONGITUDE, longitude)
            },
            PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val saveLabel = context.getString(R.string.action_save)
        val ignoreLabel = context.getString(R.string.action_ignore)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_poi_parking)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(phonePending)
            .addAction(R.drawable.ic_poi_parking, saveLabel, phoneSave)
            .addAction(R.drawable.ic_close, ignoreLabel, phoneIgnore)
            .extend(
                CarAppExtender.Builder()
                    .setImportance(NotificationManager.IMPORTANCE_HIGH)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setSmallIcon(R.drawable.ic_poi_parking)
                    .setContentIntent(carSave)
                    .addAction(R.drawable.ic_poi_parking, saveLabel, carSave)
                    .build()
            )

        val notification = builder.build()
        notificationManager.notify(NOTIFICATION_ID_REMEMBER_PARKED, notification)
        CarNotificationManager.from(context).notify(NOTIFICATION_ID_REMEMBER_PARKED, builder)
    }

    /**
     * Phone-only: walk-away after the user already saved the pin on AA (case 1).
     */
    open fun showPhoneParkedPositionConfirmed(vehicleLabel: String?) {
        if (!canPostNotifications()) return
        if (shouldSuppressDuplicate("parked_confirmed", "confirm", REMEMBER_PARKED_DEDUPE_MS)) return

        val title = context.getString(R.string.notification_parked_confirmed_title)
        val message = if (!vehicleLabel.isNullOrBlank()) {
            context.getString(R.string.notification_parked_confirmed_message, vehicleLabel)
        } else {
            context.getString(R.string.notification_parked_confirmed_message_generic)
        }

        val phoneIntent = Intent(context, MainActivity::class.java).apply {
            action = ParkedCarIntents.ACTION_VIEW_PARKED
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val phonePending = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_PHONE_PARKED_CONFIRMED,
            phoneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_poi_parking)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(phonePending)

        notificationManager.notify(NOTIFICATION_ID_PHONE_PARKED_CONFIRMED, builder.build())
    }

    /**
     * Phone-only: walk-away without saving on AA (case 2).
     * Opens remember UI with frozen candidate coords; actions Save / Ignore.
     */
    open fun showPhoneRememberParkedSuggestion(
        vehicleLabel: String?,
        vehicleId: String,
        latitude: Double,
        longitude: Double,
    ) {
        if (!canPostNotifications()) return
        if (shouldSuppressDuplicate("phone_remember_parked", "suggest", REMEMBER_PARKED_DEDUPE_MS)) return

        val title = context.getString(R.string.notification_remember_parked_title)
        val message = if (!vehicleLabel.isNullOrBlank()) {
            context.getString(R.string.notification_remember_parked_message, vehicleLabel)
        } else {
            context.getString(R.string.notification_remember_parked_message_generic)
        }

        val phoneIntent = Intent(context, MainActivity::class.java).apply {
            action = ParkedCarIntents.ACTION_REMEMBER
            putExtra(ParkedCarIntents.EXTRA_VEHICLE_ID, vehicleId)
            putExtra(ParkedCarIntents.EXTRA_LATITUDE, latitude)
            putExtra(ParkedCarIntents.EXTRA_LONGITUDE, longitude)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val phonePending = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_PHONE_REMEMBER_PARKED,
            phoneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val phoneSave = parkActionPendingBroadcast(
            requestCode = REQ_PHONE_SAVE_WALK,
            action = ParkedCarIntents.ACTION_SAVE_CANDIDATE,
            vehicleId = vehicleId,
            latitude = latitude,
            longitude = longitude,
        )
        val phoneIgnore = parkActionPendingBroadcast(
            requestCode = REQ_PHONE_IGNORE_WALK,
            action = ParkedCarIntents.ACTION_IGNORE_CANDIDATE,
            vehicleId = vehicleId,
            latitude = latitude,
            longitude = longitude,
        )

        val saveLabel = context.getString(R.string.action_save)
        val ignoreLabel = context.getString(R.string.action_ignore)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_poi_parking)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(phonePending)
            .addAction(R.drawable.ic_poi_parking, saveLabel, phoneSave)
            .addAction(R.drawable.ic_close, ignoreLabel, phoneIgnore)

        notificationManager.notify(NOTIFICATION_ID_PHONE_REMEMBER_PARKED, builder.build())
    }

    open fun cancelRememberParkedNotifications() {
        notificationManager.cancel(NOTIFICATION_ID_REMEMBER_PARKED)
        notificationManager.cancel(NOTIFICATION_ID_PHONE_REMEMBER_PARKED)
        try {
            CarNotificationManager.from(context).cancel(NOTIFICATION_ID_REMEMBER_PARKED)
        } catch (_: Exception) {
            // Car host may be unavailable outside an AA session.
        }
    }

    private fun parkActionPendingBroadcast(
        requestCode: Int,
        action: String,
        vehicleId: String,
        latitude: Double,
        longitude: Double,
    ): PendingIntent {
        val intent = Intent(context, fr.geoking.gaston.parked.ParkedCarActionReceiver::class.java).apply {
            this.action = action
            putExtra(ParkedCarIntents.EXTRA_VEHICLE_ID, vehicleId)
            putExtra(ParkedCarIntents.EXTRA_LATITUDE, latitude)
            putExtra(ParkedCarIntents.EXTRA_LONGITUDE, longitude)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Temporary phone heads-up + Android Auto HUN (auto-dismiss after [TEMPORARY_NOTIFICATION_MS]).
     */
    private fun postTemporaryCarHeadsUp(
        notificationId: Int,
        title: String,
        message: String,
        smallIcon: Int,
    ) {
        val phoneIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val phonePending = PendingIntent.getActivity(
            context,
            notificationId,
            phoneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val carIntent = Intent(context, VoiceAppService::class.java)
        val carPending = CarPendingIntent.getCarApp(
            context,
            notificationId,
            carIntent,
            PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setTimeoutAfter(TEMPORARY_NOTIFICATION_MS)
            .setOnlyAlertOnce(true)
            .setContentIntent(phonePending)
            .extend(
                CarAppExtender.Builder()
                    .setImportance(NotificationManager.IMPORTANCE_HIGH)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setSmallIcon(smallIcon)
                    .setContentIntent(carPending)
                    .build()
            )

        val notification = builder.build()
        notificationManager.notify(notificationId, notification)
        CarNotificationManager.from(context).notify(notificationId, builder)
        scheduleAutoCancel(notificationId, TEMPORARY_NOTIFICATION_MS)
    }

    private fun scheduleAutoCancel(notificationId: Int, delayMs: Long) {
        pendingCancelById.remove(notificationId)?.let { mainHandler.removeCallbacks(it) }
        val cancel = Runnable {
            notificationManager.cancel(notificationId)
            CarNotificationManager.from(context).cancel(notificationId)
            pendingCancelById.remove(notificationId)
        }
        pendingCancelById[notificationId] = cancel
        mainHandler.postDelayed(cancel, delayMs)
    }

    fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
