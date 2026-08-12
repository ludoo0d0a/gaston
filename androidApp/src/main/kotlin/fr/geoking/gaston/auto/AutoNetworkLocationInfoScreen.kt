package fr.geoking.gaston.auto

import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import fr.geoking.gaston.feature.location.LocationHelper
import fr.geoking.gaston.R
import fr.geoking.gaston.shared.location.ConnectivityManager
import fr.geoking.gaston.shared.network.CountrySource
import fr.geoking.gaston.shared.network.NetworkService
import fr.geoking.gaston.shared.network.NetworkStatus
import fr.geoking.gaston.shared.network.NetworkType
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

class AutoNetworkLocationInfoScreen(
    carContext: CarContext,
    private val networkService: NetworkService,
    private val connectivityManager: ConnectivityManager
) : Screen(carContext) {

    private var networkStatus: NetworkStatus = NetworkStatus()
    private var locationAddress: String = carContext.getString(R.string.searching_address)
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var isLoadingLocation = true
    private var isGeocoding = false

    init {
        lifecycleScope.launch {
            networkService.status.collectLatest { status ->
                networkStatus = status
                invalidate()
            }
        }
        loadLocation()
    }

    private fun loadLocation() {
        lifecycleScope.launch {
            isLoadingLocation = true
            isGeocoding = false
            locationAddress = carContext.getString(R.string.searching_address)
            invalidate()

            val location = LocationHelper.getCurrentLocation(carContext)
            if (location != null) {
                latitude = location.latitude
                longitude = location.longitude
                isLoadingLocation = false
                isGeocoding = true
                invalidate()

                val geocoder = Geocoder(carContext, Locale.getDefault())
                try {
                    val address = withTimeoutOrNull(5000) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            kotlin.coroutines.suspendCoroutine<String?> { continuation ->
                                geocoder.getFromLocation(location.latitude, location.longitude, 1, object : Geocoder.GeocodeListener {
                                    override fun onGeocode(addresses: MutableList<Address>) {
                                        continuation.resumeWith(Result.success(addresses.firstOrNull()?.let { formatAddress(it) }))
                                    }
                                    override fun onError(errorMessage: String?) {
                                        continuation.resumeWith(Result.success(null))
                                    }
                                })
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()?.let { formatAddress(it) }
                        }
                    }
                    locationAddress = address ?: carContext.getString(R.string.address_not_found)
                } catch (e: Exception) {
                    Log.e("AutoNetworkInfo", "Geocoding failed", e)
                    locationAddress = carContext.getString(R.string.network_geocoding_error)
                }
            } else {
                locationAddress = carContext.getString(R.string.location_not_available)
                isLoadingLocation = false
            }
            isGeocoding = false
            invalidate()
        }
    }

    private fun formatAddress(address: Address): String {
        val sb = StringBuilder()
        for (i in 0..address.maxAddressLineIndex) {
            sb.append(address.getAddressLine(i))
            if (i < address.maxAddressLineIndex) sb.append(", ")
        }
        return sb.toString()
    }

    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoNetworkInfo", "AutoNetworkLocationInfoScreen") {
        val paneBuilder = Pane.Builder()

        // Row 1: Connection Status
        val connectionStatus = if (networkStatus.isConnected) {
            carContext.getString(R.string.network_connected)
        } else {
            carContext.getString(R.string.network_disconnected)
        }
        val signalBars = when (networkStatus.signalLevel) {
            1 -> "▂   "
            2 -> "▂▄  "
            3 -> "▂▄▆ "
            4 -> "▂▄▆█"
            else -> "    "
        }

        paneBuilder.addRow(
            Row.Builder()
                .setTitle(connectionStatus)
                .addText(carContext.getString(R.string.network_signal, signalBars))
                .setImage(carContext.dashboardRoutesIcon())
                .build()
        )

        // Row 2: Operator & Country
        val unknown = carContext.getString(R.string.network_unknown)
        val operator = networkStatus.operatorName ?: unknown
        val country = networkStatus.countryName ?: networkStatus.countryCode ?: unknown
        val countrySource = when (networkStatus.countrySource) {
            CountrySource.LOCATION -> " " + carContext.getString(R.string.network_source_location)
            CountrySource.NETWORK -> " " + carContext.getString(R.string.network_source_network)
            CountrySource.UNKNOWN -> ""
        }
        val roamingText = if (networkStatus.isRoaming) {
            " • ${carContext.getString(R.string.network_roaming)}"
        } else {
            ""
        }

        paneBuilder.addRow(
            Row.Builder()
                .setTitle("$operator • $country$countrySource$roamingText")
                .build()
        )

        // Row 3: Address
        val searchingAddress = carContext.getString(R.string.searching_address)
        val addressTitle = if (isGeocoding && locationAddress == searchingAddress) searchingAddress else locationAddress
        paneBuilder.addRow(
            Row.Builder()
                .setTitle(addressTitle)
                .setImage(carContext.actionMapIcon())
                .build()
        )

        // Row 4: Coordinates
        val coordsText = if (isLoadingLocation) {
            carContext.getString(R.string.loading_ellipsis)
        } else {
            "${String.format("%.6f", latitude)}, ${String.format("%.6f", longitude)}"
        }
        paneBuilder.addRow(
            Row.Builder()
                .setTitle(coordsText)
                .setImage(carContext.dashboardOtherIcon())
                .build()
        )

        return@safeCarTemplate PaneTemplate.Builder(paneBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.screen_network_location_info))
                    .setStartHeaderAction(Action.BACK)
                    .addEndHeaderAction(
                        Action.Builder()
                            .setIcon(carContext.actionRefreshIcon())
                            .setOnClickListener { loadLocation() }
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun NetworkType.toReadableString(): String = when (this) {
        NetworkType.WIFI -> carContext.getString(R.string.network_wifi)
        NetworkType.FIVE_G -> "5G"
        NetworkType.FOUR_G -> "4G"
        NetworkType.THREE_G -> "3G"
        NetworkType.TWO_G -> "2G"
        NetworkType.EDGE -> carContext.getString(R.string.network_edge)
        NetworkType.GPRS -> carContext.getString(R.string.network_gprs)
        NetworkType.UNKNOWN -> carContext.getString(R.string.network_unknown)
        NetworkType.NONE -> carContext.getString(R.string.network_none)
    }
}
