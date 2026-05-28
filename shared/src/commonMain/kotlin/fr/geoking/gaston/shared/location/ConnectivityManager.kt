package fr.geoking.gaston.shared.location

import fr.geoking.gaston.shared.network.NetworkService
import fr.geoking.gaston.shared.network.NetworkSettings
import fr.geoking.gaston.shared.network.NetworkStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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

        // Update persistent settings only if they changed to avoid redundant writes
        if (status.countryCode != last.countryCode) networkSettings.lastCountryCode = status.countryCode
        if (status.countryName != last.countryName) networkSettings.lastCountryName = status.countryName
        if (status.operatorName != last.operatorName) networkSettings.lastOperatorName = status.operatorName
        if (status.isConnected != last.isConnected) networkSettings.lastIsConnected = status.isConnected
        if (status.isRoaming != last.isRoaming) networkSettings.lastIsRoaming = status.isRoaming
    }
}
