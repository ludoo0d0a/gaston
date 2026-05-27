package fr.geoking.gaston.shared.location

import fr.geoking.gaston.shared.network.NetworkService
import fr.geoking.gaston.shared.logging.log
import fr.geoking.gaston.shared.network.NetworkSettings
import fr.geoking.gaston.shared.network.NetworkStatus
import fr.geoking.gaston.shared.network.NetworkType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class ConnectivityEvent(
    val title: String,
    val message: String,
    val countryCode: String?,
    val countryName: String?,
    val operatorName: String?,
    val networkType: NetworkType,
    val isRoaming: Boolean,
    val telephonyCountryCode: String?
)

class ConnectivityManager(
    private val scope: CoroutineScope,
    private val networkService: NetworkService,
    private val networkSettings: NetworkSettings
) {
    private var lastStatus: NetworkStatus? = NetworkStatus(
        countryCode = networkSettings.lastCountryCode,
        countryName = networkSettings.lastCountryName,
        operatorName = networkSettings.lastOperatorName,
        isConnected = networkSettings.lastIsConnected,
        isRoaming = networkSettings.lastIsRoaming
    )

    private val _connectivityEvents = MutableSharedFlow<ConnectivityEvent>()
    val connectivityEvents: SharedFlow<ConnectivityEvent> = _connectivityEvents.asSharedFlow()

    init {
        scope.launch {
            networkService.status.collectLatest { status ->
                handleStatusChange(status)
                lastStatus = status
            }
        }
    }

    fun triggerManualBorderEvent() {
        scope.launch {
            emitWelcomeEvent(networkService.status.value)
        }
    }

    private suspend fun emitWelcomeEvent(status: NetworkStatus) {
        val country = status.countryName ?: status.countryCode ?: "Unknown"
        val message = if (status.isRoaming) "roaming" else "ok again"
        emitEvent(status, "Welcome to $country", message)
    }

    private suspend fun handleStatusChange(status: NetworkStatus) {
        val last = lastStatus ?: return

        // Update persistent settings only if they changed to avoid redundant writes
        if (status.countryCode != last.countryCode) networkSettings.lastCountryCode = status.countryCode
        if (status.countryName != last.countryName) networkSettings.lastCountryName = status.countryName
        if (status.operatorName != last.operatorName) networkSettings.lastOperatorName = status.operatorName
        if (status.isConnected != last.isConnected) networkSettings.lastIsConnected = status.isConnected
        if (status.isRoaming != last.isRoaming) networkSettings.lastIsRoaming = status.isRoaming

        // 1. Connection lost/regained
        if (status.isConnected != last.isConnected) {
            if (!status.isConnected) {
                emitEvent(status, "Network Connectivity", "no network")
            } else {
                val message = if (status.isRoaming) "roaming" else "ok again"
                emitEvent(status, "Network Connectivity", message)
            }
            return
        }

        if (!status.isConnected) return

        // 2. Country change (Border crossing)
        if (status.countryCode != null && last.countryCode != null && status.countryCode != last.countryCode) {
            emitWelcomeEvent(status)
            return
        }

        // 3. Operator change
        if (status.operatorName != last.operatorName) {
            if (status.operatorName.isNullOrBlank()) {
                emitEvent(status, "Network Connectivity", "no network")
            } else {
                val title = if (last.operatorName.isNullOrBlank()) {
                    "Network changed"
                } else {
                    "${last.operatorName} → ${status.operatorName}"
                }
                emitEvent(status, title, status.operatorName ?: "")
            }
            return
        }

        // 4. Roaming change (same country, same operator - rare but possible)
        if (status.isRoaming != last.isRoaming) {
            val message = if (status.isRoaming) "roaming" else "ok again"
            emitEvent(status, "Network", message)
            return
        }
    }

    private suspend fun emitEvent(status: NetworkStatus, title: String, message: String) {
        val event = ConnectivityEvent(
            title = title,
            message = message,
            countryCode = status.countryCode,
            countryName = status.countryName,
            operatorName = status.operatorName,
            networkType = status.networkType,
            isRoaming = status.isRoaming,
            telephonyCountryCode = status.telephonyCountryCode
        )
        _connectivityEvents.emit(event)
        log.d { "CONNECTIVITY_EVENT: $title - $message" }
    }
}
