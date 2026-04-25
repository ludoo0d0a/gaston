package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.gaston.SettingsManager

class AutoVehicleSettingsScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Vehicle Type")
                .addText(settings.vehicleType.name)
                .setOnClickListener {
                    screenManager.push(AutoVehicleTypeSelectionScreen(carContext, settingsManager))
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Range")
                .addText("${settings.evRangeKm} km")
                .setOnClickListener {
                    screenManager.push(AutoEvRangeSelectionScreen(carContext, settingsManager))
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Consumption")
                .addText(settings.evConsumptionKwhPer100km?.let { "$it kWh/100km" } ?: "Not set")
                .setOnClickListener {
                    screenManager.push(AutoEvConsumptionSelectionScreen(carContext, settingsManager))
                }
                .build()
        )

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle("Vehicle & Range").setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
