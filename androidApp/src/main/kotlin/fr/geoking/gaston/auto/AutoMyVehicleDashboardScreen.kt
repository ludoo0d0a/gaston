package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.di.MapDeps

class AutoMyVehicleDashboardScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager,
    private val getMapDeps: () -> MapDeps?
) : Screen(carContext) {

    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoMyVehicleDashboardScreen") {
        val settings = settingsManager.settings.value
        val gridBuilder = ItemList.Builder()

        val vehicleTitle = if (settings.vehicleBrand.isNotEmpty()) {
            "${settings.vehicleBrand} ${settings.vehicleModel}"
        } else {
            carContext.getString(R.string.search_mode_my_car)
        }

        if (settings.vehicleEnergy == "hybrid") {
            // Hybrid: separate Fuel and Electric search
            gridBuilder.addItem(
                GridItem.Builder()
                    .setTitle(carContext.getString(R.string.search_mode_fuel))
                    .setText(vehicleTitle)
                    .setImage(carContext.dashboardFuelIcon())
                    .setOnClickListener {
                        settingsManager.setMyVehicleMode()
                        screenManager.pop()
                        screenManager.push(AutoFuelDashboardScreen(carContext, settingsManager, getMapDeps))
                    }
                    .build()
            )
            gridBuilder.addItem(
                GridItem.Builder()
                    .setTitle(carContext.getString(R.string.search_mode_ev))
                    .setText(vehicleTitle)
                    .setImage(carContext.dashboardEvIcon())
                    .setOnClickListener {
                        settingsManager.setMyVehicleMode()
                        screenManager.pop()
                        screenManager.push(AutoEvDashboardScreen(carContext, settingsManager, getMapDeps))
                    }
                    .build()
            )
        } else {
            // Standard: single search action
            gridBuilder.addItem(
                GridItem.Builder()
                    .setTitle(carContext.getString(R.string.action_search))
                    .setText(vehicleTitle)
                    .setImage(carContext.dashboardMyCarIcon())
                    .setOnClickListener {
                        settingsManager.setMyVehicleMode()
                        val mapDeps = getMapDeps()
                        if (mapDeps != null) {
                            pushMapScreen(settingsManager, mapDeps, vehicleTitle)
                        }
                    }
                    .build()
            )
        }

        // Action 2: Vehicle Settings
        gridBuilder.addItem(
            GridItem.Builder()
                .setTitle(carContext.getString(R.string.cd_settings))
                .setText(carContext.getString(R.string.screen_vehicle_and_range))
                .setImage(carContext.dashboardSettingsIcon())
                .setOnClickListener {
                    screenManager.push(AutoVehicleSettingsScreen(carContext, settingsManager))
                }
                .build()
        )

        GridTemplate.Builder()
            .setSingleList(gridBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.search_mode_my_car))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }
}
