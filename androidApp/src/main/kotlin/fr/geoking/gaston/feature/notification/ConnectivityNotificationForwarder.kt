package fr.geoking.gaston.feature.notification

import fr.geoking.gaston.shared.location.ConnectivityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Forwards connectivity events to [NotificationHelper] for the whole process (phone + Android Auto).
 * Started at app launch so alerts work when only the car session is active.
 */
class ConnectivityNotificationForwarder(
    scope: CoroutineScope,
    connectivityManager: ConnectivityManager,
    notificationHelper: NotificationHelper
) {
    init {
        scope.launch {
            connectivityManager.connectivityEvents.collect { event ->
                notificationHelper.showConnectivityNotification(event.title, event.message)
            }
        }
    }

    companion object {
        fun start(
            connectivityManager: ConnectivityManager,
            notificationHelper: NotificationHelper
        ): ConnectivityNotificationForwarder {
            return ConnectivityNotificationForwarder(
                scope = CoroutineScope(SupervisorJob()),
                connectivityManager = connectivityManager,
                notificationHelper = notificationHelper
            )
        }
    }
}
