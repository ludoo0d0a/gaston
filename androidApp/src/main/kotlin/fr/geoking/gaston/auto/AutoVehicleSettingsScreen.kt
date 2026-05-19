package fr.geoking.gaston.auto

import fr.geoking.gaston.R
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
                .setTitle(carContext.getString(R.string.screen_brand_model))
                .addText(
                    if (settings.vehicleBrand.isNotEmpty()) "${settings.vehicleBrand} ${settings.vehicleModel}"
                    else carContext.getString(R.string.vehicle_not_set)
                )
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.screen_vehicle_type))
                .addText(settings.vehicleType.name)
                .setOnClickListener {
                    screenManager.push(AutoVehicleTypeSelectionScreen(carContext, settingsManager))
                }
                .build()
        )

        if (settings.vehicleEnergy == "gas" || settings.vehicleEnergy == "hybrid") {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.screen_tank_capacity))
                    .addText(
                        settings.gasTankCapacityLiters?.let { carContext.getString(R.string.vehicle_tank_format, it) }
                            ?: carContext.getString(R.string.vehicle_not_set)
                    )
                    .setOnClickListener {
                        screenManager.push(AutoGasTankCapacitySelectionScreen(carContext, settingsManager))
                    }
                    .build()
            )
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.screen_fuel_consumption))
                    .addText(
                        settings.gasConsumptionLper100km?.let {
                            carContext.getString(R.string.vehicle_consumption_format, it.toString())
                        } ?: carContext.getString(R.string.vehicle_not_set)
                    )
                    .setOnClickListener {
                        screenManager.push(AutoGasConsumptionSelectionScreen(carContext, settingsManager))
                    }
                    .build()
            )
        }

        if (settings.vehicleEnergy == "electric" || settings.vehicleEnergy == "hybrid") {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.screen_battery_capacity))
                    .addText(
                        settings.batteryCapacityKwh?.let {
                            carContext.getString(R.string.vehicle_battery_format, it.toString())
                        } ?: carContext.getString(R.string.vehicle_not_set)
                    )
                    .setOnClickListener {
                        screenManager.push(AutoBatteryCapacitySelectionScreen(carContext, settingsManager))
                    }
                    .build()
            )
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.screen_electric_range))
                    .addText(carContext.getString(R.string.vehicle_range_format, settings.evRangeKm))
                    .setOnClickListener {
                        screenManager.push(AutoEvRangeSelectionScreen(carContext, settingsManager))
                    }
                    .build()
            )

            listBuilder.addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.screen_electric_consumption))
                    .addText(
                        settings.evConsumptionKwhPer100km?.let {
                            carContext.getString(R.string.vehicle_consumption_kwh_format, it.toString())
                        } ?: carContext.getString(R.string.vehicle_not_set)
                    )
                    .setOnClickListener {
                        screenManager.push(AutoEvConsumptionSelectionScreen(carContext, settingsManager))
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle(carContext.getString(R.string.screen_vehicle_and_range)).setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
