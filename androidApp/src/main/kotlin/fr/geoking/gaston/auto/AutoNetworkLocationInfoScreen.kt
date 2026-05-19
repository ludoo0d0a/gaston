package fr.geoking.gaston.auto

import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import fr.geoking.gaston.feature.location.LocationHelper
import fr.geoking.gaston.R
import fr.geoking.gaston.shared.network.NetworkService
import fr.geoking.gaston.shared.network.NetworkStatus
import fr.geoking.gaston.shared.network.NetworkType
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

class AutoNetworkLocationInfoScreen(
    carContext: CarContext,
    private val networkService: NetworkService
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
                    locationAddress = address ?: carContext.getString(R.string.location_address_not_found)
                } catch (e: Exception) {
                    Log.e("AutoNetworkInfo", "Geocoding failed", e)
                    locationAddress = carContext.getString(R.string.geocoding_error)
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

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        // Network info
        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.network_title_status, if (networkStatus.isConnected) carContext.getString(R.string.connected) else carContext.getString(R.string.disconnected)))
                .addText(carContext.getString(R.string.network_info_type_operator, networkStatus.networkType.toReadableString(), networkStatus.operatorName ?: carContext.getString(R.string.unknown)))
                .addText(carContext.getString(R.string.network_info_country_roaming, networkStatus.countryName ?: networkStatus.countryCode ?: carContext.getString(R.string.unknown), if (networkStatus.isRoaming) carContext.getString(R.string.yes) else carContext.getString(R.string.no)))
                .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_speaker)).build())
                .build()
        )

        // Location info
        val locationRow = Row.Builder()
            .setTitle(carContext.getString(R.string.current_location_title))
        if (isLoadingLocation) {
            locationRow.addText(carContext.getString(R.string.loading_coordinates))
        } else {
            locationRow.addText(carContext.getString(R.string.lat_lon_format, latitude, longitude))
            if (isGeocoding && locationAddress == carContext.getString(R.string.searching_address)) {
                locationRow.addText(carContext.getString(R.string.searching_address))
            } else {
                locationRow.addText(locationAddress)
            }
        }
        locationRow.setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_map)).build())
        listBuilder.addItem(locationRow.build())

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.network_location))
                    .setStartHeaderAction(Action.BACK)
                    .addEndHeaderAction(
                        Action.Builder()
                            .setTitle(carContext.getString(R.string.refresh))
                            .setOnClickListener { loadLocation() }
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun NetworkType.toReadableString(): String = when (this) {
        NetworkType.WIFI -> "WiFi"
        NetworkType.FIVE_G -> "5G"
        NetworkType.FOUR_G -> "4G"
        NetworkType.THREE_G -> "3G"
        NetworkType.TWO_G -> "2G"
        NetworkType.EDGE -> "Edge"
        NetworkType.GPRS -> "GPRS"
        NetworkType.UNKNOWN -> carContext.getString(R.string.unknown)
        NetworkType.NONE -> carContext.getString(R.string.no)
    }
}
