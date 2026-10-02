package fr.geoking.gaston.feature.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.UUID

data class InAppNotification(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val message: String,
    val timestampMs: Long = System.currentTimeMillis(),
    /** Optional deep link / https URL opened when the user taps the item. */
    val actionUrl: String? = null,
    val read: Boolean = false,
)

/**
 * In-memory recent notifications for the phone notification center (bell).
 * Not mirrored to Android Auto HUN — AA stays driver-safe via [NotificationHelper] only.
 */
class InAppNotificationCenter(
    private val maxItems: Int = 50,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    private val _notifications = MutableStateFlow<List<InAppNotification>>(emptyList())
    val notifications: StateFlow<List<InAppNotification>> = _notifications.asStateFlow()

    val unreadCount: StateFlow<Int> = _notifications
        .map { list -> list.count { !it.read } }
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, 0)

    fun add(title: String, message: String, actionUrl: String? = null): InAppNotification {
        val item = InAppNotification(
            title = title,
            message = message,
            actionUrl = actionUrl?.takeIf { it.isNotBlank() },
        )
        _notifications.value = (listOf(item) + _notifications.value).take(maxItems)
        return item
    }

    fun markRead(id: String) {
        _notifications.value = _notifications.value.map { n ->
            if (n.id == id && !n.read) n.copy(read = true) else n
        }
    }

    fun markAllRead() {
        if (_notifications.value.none { !it.read }) return
        _notifications.value = _notifications.value.map { n ->
            if (n.read) n else n.copy(read = true)
        }
    }

    fun clear() {
        _notifications.value = emptyList()
    }
}
