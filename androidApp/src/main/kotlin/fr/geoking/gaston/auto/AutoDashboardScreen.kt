package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.di.MapDeps
import fr.geoking.gaston.repository.FuelForecastRepository
import fr.geoking.gaston.shared.location.ConnectivityManager
import fr.geoking.gaston.shared.network.NetworkService

class AutoDashboardScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager,
    private val networkService: NetworkService,
    private val fuelForecastRepository: FuelForecastRepository,
    private val connectivityManager: ConnectivityManager,
    private val getMapDeps: () -> MapDeps?
) : Screen(carContext) {

    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoDashboardScreen") {
        val gridBuilder = ItemList.Builder()

        // 1. Fuel
        gridBuilder.addItem(
            GridItem.Builder()
                .setTitle(carContext.getString(R.string.search_mode_fuel))
                .setImage(carContext.dashboardFuelIcon())
                .setOnClickListener {
                    screenManager.push(AutoFuelDashboardScreen(carContext, settingsManager, getMapDeps))
                }
                .build()
        )

        // 2. EV
        gridBuilder.addItem(
            GridItem.Builder()
                .setTitle(carContext.getString(R.string.search_mode_ev))
                .setImage(carContext.dashboardEvIcon())
                .setOnClickListener {
                    screenManager.push(AutoEvDashboardScreen(carContext, settingsManager, getMapDeps))
                }
                .build()
        )

        // 3. My Vehicle
        gridBuilder.addItem(
            GridItem.Builder()
                .setTitle(carContext.getString(R.string.search_mode_my_car))
                .setImage(carContext.dashboardMyCarIcon())
                .setOnClickListener {
                    screenManager.push(AutoMyVehicleDashboardScreen(carContext, settingsManager, getMapDeps))
                }
                .build()
        )

        // 4. Other
        gridBuilder.addItem(
            GridItem.Builder()
                .setTitle(carContext.getString(R.string.search_mode_other))
                .setImage(carContext.dashboardOtherIcon())
                .setOnClickListener {
                    screenManager.push(AutoOtherDashboardScreen(carContext, settingsManager, getMapDeps))
                }
                .build()
        )

        // 5. Routes
        gridBuilder.addItem(
            GridItem.Builder()
                .setTitle(carContext.getString(R.string.dashboard_routes))
                .setImage(carContext.dashboardRoutesIcon())
                .setOnClickListener {
                    val mapDeps = getMapDeps() ?: return@setOnClickListener
                    screenManager.push(
                        AutoRoutePlanningScreen(
                            carContext = carContext,
                            routePlanner = mapDeps.routePlanner,
                            routingClient = mapDeps.routingClient,
                            poiProvider = mapDeps.poiProvider,
                            geocodingClient = mapDeps.geocodingClient,
                            settingsManager = settingsManager
                        )
                    )
                }
                .build()
        )

        // 6. Connectivity
        gridBuilder.addItem(
            GridItem.Builder()
                .setTitle(carContext.getString(R.string.dashboard_network))
                .setImage(carContext.dashboardNetworkIcon())
                .setOnClickListener {
                    screenManager.push(AutoNetworkLocationInfoScreen(carContext, networkService, connectivityManager))
                }
                .build()
        )

        // 7. Emergency
        gridBuilder.addItem(
            GridItem.Builder()
                .setTitle(carContext.getString(R.string.dashboard_emergency))
                .setImage(carContext.dashboardEmergencyIcon())
                .setOnClickListener {
                    screenManager.push(AutoEmergencyScreen(carContext, networkService, connectivityManager))
                }
                .build()
        )

        // 8. More
        gridBuilder.addItem(
            GridItem.Builder()
                .setTitle(carContext.getString(R.string.screen_more))
                .setImage(carContext.dashboardSettingsIcon())
                .setOnClickListener {
                    pushMoreOptionsScreen()
                }
                .build()
        )

        val appTitle = if (settingsManager.settings.value.hasPremiumFeatures) "Gaston Premium" else "Gaston"
        GridTemplate.Builder()
            .setSingleList(gridBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle(appTitle)
                    .setStartHeaderAction(Action.APP_ICON)
                    .build()
            )
            .build()
    }

    private fun pushMoreOptionsScreen() {
        screenManager.push(
            object : Screen(carContext) {
                override fun onGetTemplate(): Template {
                    val moreList = ItemList.Builder()
                        .addItem(
                            Row.Builder()
                                .setTitle(carContext.getString(R.string.screen_fuel_outlook))
                                .setImage(carContext.dashboardFuelIcon())
                                .setOnClickListener {
                                    screenManager.push(AutoFuelForecastScreen(carContext, settingsManager, fuelForecastRepository))
                                }
                                .build()
                        )
                        .addItem(
                            Row.Builder()
                                .setTitle(carContext.getString(R.string.cd_map_settings))
                                .setImage(carContext.dashboardSettingsIcon())
                                .setOnClickListener {
                                    screenManager.push(AutoMapSettingsScreen(carContext, settingsManager))
                                }
                                .build()
                        )
                        .addItem(
                            Row.Builder()
                                .setTitle(carContext.getString(R.string.screen_about))
                                .setImage(carContext.carIconUntinted(R.drawable.ic_launcher_foreground))
                                .setOnClickListener {
                                    screenManager.push(AutoAboutScreen(carContext))
                                }
                                .build()
                        )
                        .build()

                    return ListTemplate.Builder()
                        .setHeader(Header.Builder().setTitle(carContext.getString(R.string.screen_more)).setStartHeaderAction(Action.BACK).build())
                        .setSingleList(moreList)
                        .build()
                }
            }
        )
    }
}
