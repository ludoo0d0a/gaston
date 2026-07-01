package fr.geoking.gaston.shared.location

import fr.geoking.gaston.shared.network.NetworkService
import fr.geoking.gaston.shared.network.NetworkSettings
import fr.geoking.gaston.shared.network.NetworkStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ConnectivityManager(
    private val scope: CoroutineScope,
    private val networkService: NetworkService,
    private val networkSettings: NetworkSettings
) {
    private val _borderCrossingEvents = MutableSharedFlow<String>()
    val borderCrossingEvents: SharedFlow<String> = _borderCrossingEvents.asSharedFlow()

    private var lastStatus: NetworkStatus? = NetworkStatus(
        countryCode = networkSettings.lastCountryCode,
        countryName = networkSettings.lastCountryName,
        operatorName = networkSettings.lastOperatorName,
        isConnected = networkSettings.lastIsConnected,
        isRoaming = networkSettings.lastIsRoaming
    )

    init {
        scope.launch {
            networkService.status.collectLatest { status ->
                handleStatusChange(status)
                lastStatus = status
            }
        }
    }

    private fun handleStatusChange(status: NetworkStatus) {
        val last = lastStatus ?: return

        // Border crossing detection: countryCode changed from a valid value to another valid value
        if (status.countryCode != null && last.countryCode != null && status.countryCode != last.countryCode) {
            val countryName = status.countryName ?: status.countryCode
            scope.launch {
                _borderCrossingEvents.emit(countryName)
            }
        }

        // Update persistent settings only if they changed to avoid redundant writes
        if (status.countryCode != last.countryCode) networkSettings.lastCountryCode = status.countryCode
        if (status.countryName != last.countryName) networkSettings.lastCountryName = status.countryName
        if (status.operatorName != last.operatorName) networkSettings.lastOperatorName = status.operatorName
        if (status.isConnected != last.isConnected) networkSettings.lastIsConnected = status.isConnected
        if (status.isRoaming != last.isRoaming) networkSettings.lastIsRoaming = status.isRoaming
    }
}
