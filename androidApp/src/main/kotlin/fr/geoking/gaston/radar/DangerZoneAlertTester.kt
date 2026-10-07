package fr.geoking.gaston.radar

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Phone-side request bus for the developer "test danger zone alert" action.
 * [DangerZoneAlertCoordinator] collects and runs [DangerZoneAlertManager.triggerTestAlert]
 * (beep + TTS + HUN + HUD).
 */
class DangerZoneAlertTester {
    data class Request(val speedLimitKmH: Int? = DangerZoneAlertManager.TEST_ALERT_SPEED_LIMIT_KMH)

    private val _requests = MutableSharedFlow<Request>(extraBufferCapacity = 1)
    val requests: SharedFlow<Request> = _requests.asSharedFlow()

    fun trigger(speedLimitKmH: Int? = DangerZoneAlertManager.TEST_ALERT_SPEED_LIMIT_KMH) {
        _requests.tryEmit(Request(speedLimitKmH))
    }
}
