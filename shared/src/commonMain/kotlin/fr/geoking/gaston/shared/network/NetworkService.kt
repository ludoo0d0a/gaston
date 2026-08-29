package fr.geoking.gaston.shared.network

import kotlinx.coroutines.flow.StateFlow

enum class NetworkType {
    GPRS,
    EDGE,
    TWO_G,
    THREE_G,
    FOUR_G,
    FIVE_G,
    WIFI,
    UNKNOWN,
    NONE
}

enum class CountrySource {
    LOCATION,
    NETWORK,
    UNKNOWN
}

data class NetworkStatus(
    val countryCode: String? = null,
    val countryName: String? = null,
    val countrySource: CountrySource = CountrySource.UNKNOWN,
    /** Country from GPS / map position (geocode or offline region). */
    val locationCountryCode: String? = null,
    val locationCountryName: String? = null,
    /** Country from cellular network (telephony MCC / networkCountryIso). */
    val telephonyCountryCode: String? = null,
    val networkType: NetworkType = NetworkType.UNKNOWN,
    val isRoaming: Boolean = false,
    val operatorName: String? = null,
    val isConnected: Boolean = false,
    val signalLevel: Int = 0 // 0 to 4
)

interface NetworkService {
    val status: StateFlow<NetworkStatus>
    suspend fun getCurrentStatus(): NetworkStatus
}
