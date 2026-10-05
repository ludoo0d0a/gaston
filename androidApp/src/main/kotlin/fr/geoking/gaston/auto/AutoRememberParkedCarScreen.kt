package fr.geoking.gaston.auto

import android.content.Intent
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
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.activeVehicle
import fr.geoking.gaston.feature.location.LocationHelper
import fr.geoking.gaston.intent.IntentNavigationHelper
import fr.geoking.gaston.parked.ParkCandidateSaveHelper
import fr.geoking.gaston.parked.ParkCandidateStore
import fr.geoking.gaston.parkedPositionFor
import fr.geoking.gaston.vehicleById
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Remember / find parked car on Android Auto.
 * Default vehicle = garage active; tap the vehicle row to change.
 */
class AutoRememberParkedCarScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager,
    private var selectedVehicleId: String = settingsManager.settings.value.activeVehicleId,
) : Screen(carContext), KoinComponent {

    private val candidateStore: ParkCandidateStore by inject()

    private var statusMessage: String? = null
    private var isSaving = false

    override fun onGetTemplate(): Template = safeCarTemplate(
        carContext = carContext,
        logTag = "AutoRememberParkedCarScreen",
        templateName = "ListTemplate",
    ) {
        val settings = settingsManager.settings.value
        val vehicleId = selectedVehicleId.ifBlank { settings.activeVehicleId }
            .ifBlank { settings.vehicles.firstOrNull()?.id.orEmpty() }
            .ifBlank { "default" }
        selectedVehicleId = vehicleId

        val vehicle = settings.vehicleById(vehicleId) ?: settings.activeVehicle()
        val vehicleLabel = vehicle?.displayLabel()?.ifBlank { null }
            ?: listOf(settings.vehicleBrand, settings.vehicleModel).filter { it.isNotBlank() }.joinToString(" ")
                .ifBlank { carContext.getString(R.string.search_mode_my_car) }

        val parked = settings.parkedPositionFor(vehicleId)
        val listBuilder = ItemList.Builder()

        listBuilder.addItem(
            Row.Builder()
                .setTitle(vehicleLabel)
                .addText(carContext.getString(R.string.parked_car_tap_to_change_vehicle))
                .setBrowsable(true)
                .setOnClickListener {
                    screenManager.push(
                        AutoParkedCarVehicleSelectionScreen(
                            carContext = carContext,
                            settingsManager = settingsManager,
                            selectedVehicleId = vehicleId,
                            onSelected = { id ->
                                selectedVehicleId = id
                                statusMessage = null
                                invalidate()
                            },
                        )
                    )
                }
                .build()
        )

        val saveTitle = when {
            isSaving -> carContext.getString(R.string.parked_car_saving)
            parked != null -> carContext.getString(R.string.parked_car_update)
            else -> carContext.getString(R.string.parked_car_remember)
        }
        val saveSubtitle = statusMessage
            ?: if (parked != null) {
                carContext.getString(R.string.parked_car_parked_at, parked.formattedTimestamp())
            } else {
                carContext.getString(R.string.parked_car_remember_subtitle)
            }
        listBuilder.addItem(
            Row.Builder()
                .setTitle(saveTitle)
                .addText(saveSubtitle)
                .setOnClickListener {
                    if (!isSaving) saveCurrentLocation(vehicleId)
                }
                .build()
        )

        if (parked != null) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.parked_car_navigate))
                    .addText(
                        carContext.getString(R.string.parked_car_parked_at, parked.formattedTimestamp())
                    )
                    .setOnClickListener {
                        val uri = IntentNavigationHelper.getNavigationUri(parked.latitude, parked.longitude)
                        carContext.startCarApp(Intent(CarContext.ACTION_NAVIGATE).apply { data = uri })
                    }
                    .build()
            )
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.parked_car_clear))
                    .setOnClickListener {
                        settingsManager.clearParkedPosition(vehicleId)
                        statusMessage = carContext.getString(R.string.parked_car_cleared)
                        invalidate()
                    }
                    .build()
            )
        }

        ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.parked_car_title))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }

    private fun saveCurrentLocation(vehicleId: String) {
        isSaving = true
        statusMessage = null
        invalidate()
        lifecycleScope.launch {
            try {
                val candidate = candidateStore.get()
                val loc = LocationHelper.getCurrentLocation(carContext)
                val coords = ParkCandidateSaveHelper.resolveSaveCoords(
                    candidate = candidate,
                    vehicleId = vehicleId,
                    fallbackLat = loc?.latitude ?: settingsManager.settings.value.lastKnownLat,
                    fallbackLon = loc?.longitude ?: settingsManager.settings.value.lastKnownLon,
                )
                if (coords == null) {
                    statusMessage = carContext.getString(R.string.parked_car_location_unavailable)
                } else {
                    val (lat, lon) = coords
                    settingsManager.saveParkedPosition(lat, lon, vehicleId)
                    if (candidate != null && candidate.vehicleId == vehicleId) {
                        candidateStore.markSavedFromAa()
                    } else if (loc != null) {
                        settingsManager.saveLastKnownLocation(lat, lon)
                    }
                    statusMessage = carContext.getString(R.string.parked_car_saved)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save parked position", e)
                statusMessage = carContext.getString(R.string.parked_car_location_unavailable)
            } finally {
                isSaving = false
                invalidate()
            }
        }
    }

    companion object {
        private const val TAG = "AutoRememberParkedCar"
    }
}
