package fr.geoking.gaston.shared.location

import fr.geoking.gaston.shared.network.NetworkService
import fr.geoking.gaston.shared.logging.log
import fr.geoking.gaston.shared.network.NetworkType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class BorderCrossingEvent(
    val countryCode: String,
    val countryName: String?,
    val operatorName: String?,
    val networkType: NetworkType,
    val isRoaming: Boolean,
    val telephonyCountryCode: String?
)

class BorderCrossingManager(
    private val scope: CoroutineScope,
    private val networkService: NetworkService
) {
    private var lastCountryCode: String? = null

    private val _borderCrossingEvents = MutableSharedFlow<BorderCrossingEvent>()
    val borderCrossingEvents: SharedFlow<BorderCrossingEvent> = _borderCrossingEvents.asSharedFlow()

    init {
        scope.launch {
            networkService.status.collectLatest { status ->
                val currentCountry = status.countryCode
                if (currentCountry != null && lastCountryCode != null && currentCountry != lastCountryCode) {
                    val event = BorderCrossingEvent(
                        countryCode = currentCountry,
                        countryName = status.countryName,
                        operatorName = status.operatorName,
                        networkType = status.networkType,
                        isRoaming = status.isRoaming,
                        telephonyCountryCode = status.telephonyCountryCode
                    )
                    _borderCrossingEvents.emit(event)
                    log.d { "CROSS_BORDER_EVENT_EMITTED: $currentCountry" }
                }
                if (currentCountry != null) {
                    lastCountryCode = currentCountry
                }
            }
        }
    }
}
