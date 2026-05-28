package fr.geoking.gaston.auto

import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import fr.geoking.gaston.R
import fr.geoking.gaston.feature.emergency.EmergencyCategory
import fr.geoking.gaston.feature.emergency.EmergencyContact
import fr.geoking.gaston.feature.emergency.EmergencyContactRegistry
import fr.geoking.gaston.feature.location.LocationHelper
import fr.geoking.gaston.shared.location.ConnectivityManager
import fr.geoking.gaston.shared.network.NetworkService
import fr.geoking.gaston.shared.network.NetworkStatus
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

class AutoEmergencyScreen(
    carContext: CarContext,
    private val networkService: NetworkService,
    private val connectivityManager: ConnectivityManager
) : Screen(carContext) {

    private var networkStatus: NetworkStatus = networkService.status.value
    private var locationAddress: String? = null
    private var latitude: Double? = null
    private var longitude: Double? = null
    private var detectedCountryCode: String? = null
    private var isLoadingLocation = true

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
            invalidate()

            val location = LocationHelper.getCurrentLocation(carContext)
            if (location != null) {
                latitude = location.latitude
                longitude = location.longitude
                invalidate()

                val geocoder = Geocoder(carContext, Locale.getDefault())
                try {
                    val addressObj = withTimeoutOrNull(5000) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            kotlin.coroutines.suspendCoroutine<Address?> { continuation ->
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
                    }
                    locationAddress = addressObj?.let { formatAddress(it) }
                    detectedCountryCode = addressObj?.countryCode
                } catch (e: Exception) {
                    Log.e("AutoEmergency", "Geocoding failed", e)
                }
            }
            isLoadingLocation = false
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

        val countryCode = detectedCountryCode ?: networkStatus.countryCode
        val universalNumber = universalNumberFor(countryCode)
        val countryName = EmergencyContactRegistry.countryDisplayName(countryCode) ?: networkStatus.countryName

        // Universal Emergency Number
        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.emergency_call, universalNumber))
                .addText(carContext.getString(R.string.emergency_universal_number))
                .setImage(carContext.dashboardEmergencyIcon())
                .setOnClickListener { dial(universalNumber) }
                .build()
        )

        // Location Info
        val locationRow = Row.Builder()
            .setTitle(carContext.getString(R.string.screen_your_current_location))
        if (isLoadingLocation) {
            locationRow.addText(carContext.getString(R.string.emergency_locating_short))
        } else if (latitude != null && longitude != null) {
            locationRow.addText(
                carContext.getString(
                    R.string.emergency_coords,
                    String.format("%.6f", latitude),
                    String.format("%.6f", longitude)
                )
            )
            locationRow.addText(locationAddress ?: carContext.getString(R.string.emergency_address_unavailable))
        } else {
            locationRow.addText(carContext.getString(R.string.emergency_location_unavailable))
        }
        locationRow.setImage(carContext.actionMapIcon())
        listBuilder.addItem(locationRow.build())

        // Local Emergency Numbers
        val contacts = EmergencyContactRegistry.contactsFor(countryCode)
        if (contacts.isNotEmpty()) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(
                        if (countryName != null) {
                            carContext.getString(R.string.emergency_useful_numbers_auto_country, countryName)
                        } else {
                            carContext.getString(R.string.emergency_useful_numbers)
                        }
                    )
                    .addText(carContext.getString(R.string.emergency_contacts_count, contacts.size))
                    .setImage(carContext.carIcon(R.drawable.ic_speaker, AutoCarIcons.muted))
                    .setOnClickListener {
                        screenManager.push(AutoEmergencyContactsScreen(carContext, countryName, contacts))
                    }
                    .setBrowsable(true)
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.dashboard_emergency))
                    .setStartHeaderAction(Action.BACK)
                    .addEndHeaderAction(
                        Action.Builder()
                            .setIcon(carContext.actionHistoryIcon())
                            .setOnClickListener { loadLocation() }
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun dial(number: String) {
        val sanitized = number.replace(" ", "")
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$sanitized")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            carContext.startActivity(intent)
        } catch (e: Exception) {
            Log.e("AutoEmergency", "Dialing failed", e)
        }
    }

    private fun universalNumberFor(countryCode: String?): String {
        val cc = countryCode?.uppercase()
        return when (cc) {
            "US", "CA", "MX" -> "911"
            else -> "112"
        }
    }
}

class AutoEmergencyContactsScreen(
    carContext: CarContext,
    private val countryName: String?,
    private val contacts: List<EmergencyContact>
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        for (contact in contacts) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(contact.label)
                    .addText("${contact.number}${if (contact.description != null) " - ${contact.description}" else ""}")
                    .setImage(carContext.emergencyCategoryIcon(contact.category))
                    .setOnClickListener { dial(contact.number) }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle(if (countryName != null) "Useful Numbers - $countryName" else "Useful Numbers")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }

    private fun dial(number: String) {
        val sanitized = number.replace(" ", "")
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$sanitized")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            carContext.startActivity(intent)
        } catch (e: Exception) {
            Log.e("AutoEmergency", "Dialing failed", e)
        }
    }

}
