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
                .setTitle("Brand & Model")
                .addText(if (settings.vehicleBrand.isNotEmpty()) "${settings.vehicleBrand} ${settings.vehicleModel}" else "Not set")
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Vehicle Type")
                .addText(settings.vehicleType.name)
                .setOnClickListener {
                    screenManager.push(AutoVehicleTypeSelectionScreen(carContext, settingsManager))
                }
                .build()
        )

        if (settings.vehicleEnergy == "gas" || settings.vehicleEnergy == "hybrid") {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Tank Capacity")
                    .addText(settings.gasTankCapacityLiters?.let { "$it L" } ?: "Not set")
                    .setOnClickListener {
                        screenManager.push(AutoGasTankCapacitySelectionScreen(carContext, settingsManager))
                    }
                    .build()
            )
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Fuel Consumption")
                    .addText(settings.gasConsumptionLper100km?.let { "$it L/100km" } ?: "Not set")
                    .setOnClickListener {
                        screenManager.push(AutoGasConsumptionSelectionScreen(carContext, settingsManager))
                    }
                    .build()
            )
        }

        if (settings.vehicleEnergy == "electric" || settings.vehicleEnergy == "hybrid") {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Battery Capacity")
                    .addText(settings.batteryCapacityKwh?.let { "$it kWh" } ?: "Not set")
                    .setOnClickListener {
                        screenManager.push(AutoBatteryCapacitySelectionScreen(carContext, settingsManager))
                    }
                    .build()
            )
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Electric Range")
                    .addText("${settings.evRangeKm} km")
                    .setOnClickListener {
                        screenManager.push(AutoEvRangeSelectionScreen(carContext, settingsManager))
                    }
                    .build()
            )

            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Electric Consumption")
                    .addText(settings.evConsumptionKwhPer100km?.let { "$it kWh/100km" } ?: "Not set")
                    .setOnClickListener {
                        screenManager.push(AutoEvConsumptionSelectionScreen(carContext, settingsManager))
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle("Vehicle & Range").setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
