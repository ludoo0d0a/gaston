package fr.geoking.gaston.feature.network

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import fr.geoking.gaston.feature.location.LocationHelper
import fr.geoking.gaston.shared.network.NetworkService
import fr.geoking.gaston.shared.network.NetworkStatus
import fr.geoking.gaston.shared.network.NetworkType
import fr.geoking.gaston.shared.platform.PermissionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private const val TAG = "AndroidNetworkService"

class AndroidNetworkService(
    private val context: Context,
    private val scope: CoroutineScope,
    private val permissionManager: PermissionManager
) : NetworkService {

    private val _status = MutableStateFlow(NetworkStatus())
    override val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    init {
        // Ensure map module is loaded for Geocoder/Weather deps if needed by LocationHelper indirectly
        // although LocationHelper seems standalone here, MapModuleLoader.ensureLoaded() is often needed
        // for some providers.

        // Requires manifest ACCESS_NETWORK_STATE (see ConnectivityManager.registerDefaultNetworkCallback).
        try {
            connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    updateStatus()
                }
                override fun onLost(network: android.net.Network) {
                    updateStatus()
                }
                override fun onCapabilitiesChanged(network: android.net.Network, capabilities: NetworkCapabilities) {
                    updateStatus()
                }
            })
        } catch (e: SecurityException) {
            Log.e(TAG, "registerDefaultNetworkCallback failed (ACCESS_NETWORK_STATE?)", e)
        }

        // Initial status update
        updateStatus()
    }

    private fun updateStatus() {
        scope.launch(Dispatchers.IO) {
            val newStatus = fetchCurrentStatus()
            _status.value = newStatus
        }
    }

    override suspend fun getCurrentStatus(): NetworkStatus = withContext(Dispatchers.IO) {
        val newStatus = fetchCurrentStatus()
        _status.value = newStatus
        newStatus
    }

    private suspend fun fetchCurrentStatus(): NetworkStatus {
        return try {
            val networkType = getNetworkType()
            val isConnected = isNetworkConnected()
            val isRoaming = telephonyManager.isNetworkRoaming
            val operatorName = telephonyManager.networkOperatorName

            val signalLevel = getSignalLevel()

            // Try to get country from Telephony (MCC)
            val telephonyCountry = telephonyManager.networkCountryIso?.uppercase(Locale.US)?.takeIf { it.isNotBlank() }

            // Try to get country from Location (GPS) if permission granted
            var locationCountryCode: String? = null
            var locationCountryName: String? = null

            if (permissionManager.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                val location = LocationHelper.getCurrentLocation(context)
                if (location != null) {
                    val address = getAddress(location)
                    locationCountryCode = address?.countryCode?.uppercase(Locale.US)
                    locationCountryName = address?.countryName
                }
            }

            // Prefer GPS-based country code for cross-border accuracy, fallback to Telephony
            val finalCountryCode = locationCountryCode ?: telephonyCountry

            NetworkStatus(
                countryCode = finalCountryCode,
                countryName = locationCountryName, // Might be null if only telephony worked
                telephonyCountryCode = telephonyCountry,
                networkType = networkType,
                isRoaming = isRoaming,
                operatorName = operatorName,
                isConnected = isConnected,
                signalLevel = signalLevel
            )
        } catch (e: SecurityException) {
            // e.g. cellular signal / telephony fields without READ_PHONE_STATE on some API levels
            Log.w(TAG, "fetchCurrentStatus limited by permissions", e)
            NetworkStatus(isConnected = runCatching { isNetworkConnected() }.getOrDefault(false))
        }
    }

    private fun getSignalLevel(): Int {
        val activeNetwork = connectivityManager.activeNetwork ?: return 0
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return 0

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                val rssi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val wifiInfo = capabilities.transportInfo as? android.net.wifi.WifiInfo
                    wifiInfo?.rssi ?: -127
                } else {
                    @Suppress("DEPRECATION")
                    wifiManager.connectionInfo.rssi
                }
                @Suppress("DEPRECATION")
                WifiManager.calculateSignalLevel(rssi, 5) // returns 0-4
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    telephonyManager.signalStrength?.level ?: 0
                } else 0
            }
            else -> 0
        }
    }

    private fun getNetworkType(): NetworkType {
        val activeNetwork = connectivityManager.activeNetwork ?: return NetworkType.NONE
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return NetworkType.NONE

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                if (permissionManager.hasPermission(Manifest.permission.READ_PHONE_STATE)) {
                    val telephonyNetworkType = readTelephonyNetworkTypeWithPhonePermission()
                    mapTelephonyNetworkType(telephonyNetworkType)
                } else {
                    NetworkType.UNKNOWN
                }
            }
            else -> NetworkType.UNKNOWN
        }
    }

    @SuppressLint("MissingPermission")
    private fun readTelephonyNetworkTypeWithPhonePermission(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            telephonyManager.dataNetworkType
        } else {
            @Suppress("DEPRECATION")
            telephonyManager.networkType
        }
    }

    @Suppress("DEPRECATION")
    private fun mapTelephonyNetworkType(type: Int): NetworkType {
        return when (type) {
            TelephonyManager.NETWORK_TYPE_GPRS -> NetworkType.GPRS
            TelephonyManager.NETWORK_TYPE_EDGE -> NetworkType.EDGE
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_IDEN -> NetworkType.TWO_G
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_EHRPD,
            TelephonyManager.NETWORK_TYPE_HSPAP -> NetworkType.THREE_G
            TelephonyManager.NETWORK_TYPE_LTE,
            TelephonyManager.NETWORK_TYPE_IWLAN -> NetworkType.FOUR_G
            TelephonyManager.NETWORK_TYPE_NR -> NetworkType.FIVE_G
            else -> NetworkType.UNKNOWN
        }
    }

    private fun isNetworkConnected(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun getAddress(location: Location): Address? {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                kotlin.coroutines.suspendCoroutine { continuation ->
                    geocoder.getFromLocation(location.latitude, location.longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            continuation.resumeWith(Result.success(addresses.firstOrNull()))
                        }
                        override fun onError(errorMessage: String?) {
                            continuation.resumeWith(Result.success(null))
                        }
                    })
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
            }
        } catch (e: Exception) {
            null
        }
    }
}
