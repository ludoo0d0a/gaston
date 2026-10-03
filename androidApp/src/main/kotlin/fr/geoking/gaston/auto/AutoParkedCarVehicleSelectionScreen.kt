package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager

/**
 * Pick which garage vehicle to attach the parked pin to.
 */
class AutoParkedCarVehicleSelectionScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager,
    private val selectedVehicleId: String,
    private val onSelected: (String) -> Unit,
) : Screen(carContext) {

    override fun onGetTemplate(): Template = safeCarTemplate(
        carContext = carContext,
        logTag = "AutoParkedCarVehicleSelectionScreen",
        templateName = "ListTemplate",
    ) {
        val settings = settingsManager.settings.value
        val listLimit = try {
            carContext.getCarService(ConstraintManager::class.java)
                .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        } catch (_: Exception) {
            6
        }
        val listBuilder = ItemList.Builder()

        val vehicles = settings.vehicles
        if (vehicles.isEmpty()) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.search_mode_my_car))
                    .addText(carContext.getString(R.string.parked_car_no_garage_hint))
                    .setOnClickListener {
                        onSelected(settings.activeVehicleId.ifBlank { "default" })
                        screenManager.pop()
                    }
                    .build()
            )
        } else {
            vehicles.take(listLimit).forEachIndexed { index, vehicle ->
                val label = vehicle.displayLabel()
                    .ifBlank { carContext.getString(R.string.vehicle_unnamed, index + 1) }
                val title = if (vehicle.id == selectedVehicleId) {
                    carContext.getString(R.string.parked_car_vehicle_selected, label)
                } else {
                    label
                }
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(title)
                        .setOnClickListener {
                            onSelected(vehicle.id)
                            screenManager.pop()
                        }
                        .build()
                )
            }
        }

        ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.parked_car_change_vehicle))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }
}
