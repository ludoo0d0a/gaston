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
import fr.geoking.gaston.feature.maintenance.MaintenanceRepository
import fr.geoking.gaston.latestParkedPosition
import org.koin.core.context.GlobalContext

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

        gridBuilder.addItem(
            GridItem.Builder()
                .setTitle(carContext.getString(R.string.maintenance_auto_title))
                .setText(carContext.getString(R.string.maintenance_auto_last_service))
                .setImage(carContext.dashboardMaintenanceIcon())
                .setOnClickListener {
                    val repo = GlobalContext.get().get<MaintenanceRepository>()
                    screenManager.push(AutoMaintenanceLogScreen(carContext, settingsManager, repo))
                }
                .build()
        )

        val parkedSubtitle = settings.latestParkedPosition()?.let { parked ->
            carContext.getString(R.string.parked_car_parked_at, parked.formattedTimestamp())
        } ?: carContext.getString(R.string.parked_car_remember_subtitle)
        gridBuilder.addItem(
            GridItem.Builder()
                .setTitle(carContext.getString(R.string.parked_car_title))
                .setText(parkedSubtitle)
                .setImage(carContext.dashboardParkedCarIcon())
                .setOnClickListener {
                    screenManager.push(AutoRememberParkedCarScreen(carContext, settingsManager))
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
