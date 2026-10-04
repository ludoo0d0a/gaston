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

/**
 * Root Android Auto hub — ≤6 grid tiles so strict hosts (CONTENT_LIMIT_TYPE_GRID min 6) show all items.
 * Primary search (Fuel / EV) stays one tap; secondary tools live under Places / Vehicle / More.
 */
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

        // 3. Places (amenities + danger zones)
        gridBuilder.addItem(
            GridItem.Builder()
                .setTitle(carContext.getString(R.string.search_mode_other))
                .setImage(carContext.dashboardOtherIcon())
                .setOnClickListener {
                    screenManager.push(AutoOtherDashboardScreen(carContext, settingsManager, getMapDeps))
                }
                .build()
        )

        // 4. Emergency
        gridBuilder.addItem(
            GridItem.Builder()
                .setTitle(carContext.getString(R.string.dashboard_emergency))
                .setImage(carContext.dashboardEmergencyIcon())
                .setOnClickListener {
                    screenManager.push(AutoEmergencyScreen(carContext, networkService, connectivityManager))
                }
                .build()
        )

        // 5. My vehicle (search + settings + maintenance)
        gridBuilder.addItem(
            GridItem.Builder()
                .setTitle(carContext.getString(R.string.search_mode_my_car))
                .setImage(carContext.dashboardMyCarIcon())
                .setOnClickListener {
                    screenManager.push(AutoMyVehicleDashboardScreen(carContext, settingsManager, getMapDeps))
                }
                .build()
        )

        // 6. More (favorites, routes, network, settings…)
        gridBuilder.addItem(
            GridItem.Builder()
                .setTitle(carContext.getString(R.string.screen_more))
                .setImage(carContext.dashboardSettingsIcon())
                .setOnClickListener {
                    pushMoreOptionsScreen()
                }
                .build()
        )

        val appTitle = if (settingsManager.settings.value.hasPremiumFeatures) {
            carContext.getString(R.string.premium_brand_title)
        } else {
            carContext.getString(R.string.app_name)
        }
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
                override fun onGetTemplate(): Template = safeCarTemplate(
                    carContext = carContext,
                    logTag = "AutoMoreOptionsScreen",
                    templateName = "ListTemplate",
                ) {
                    val moreList = ItemList.Builder()
                        .addItem(
                            Row.Builder()
                                .setTitle(carContext.getString(R.string.screen_favorites))
                                .setImage(carContext.dashboardFavoritesIcon())
                                .setBrowsable(true)
                                .setOnClickListener {
                                    screenManager.push(AutoFavoritesScreen(carContext, settingsManager, getMapDeps))
                                }
                                .build()
                        )
                        .addItem(
                            Row.Builder()
                                .setTitle(carContext.getString(R.string.dashboard_routes))
                                .setImage(carContext.dashboardRoutesIcon())
                                .setBrowsable(true)
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
                        .addItem(
                            Row.Builder()
                                .setTitle(carContext.getString(R.string.dashboard_network))
                                .setImage(carContext.dashboardNetworkIcon())
                                .setBrowsable(true)
                                .setOnClickListener {
                                    screenManager.push(
                                        AutoNetworkLocationInfoScreen(carContext, networkService, connectivityManager)
                                    )
                                }
                                .build()
                        )
                        .addItem(
                            Row.Builder()
                                .setTitle(carContext.getString(R.string.screen_fuel_outlook))
                                .setImage(carContext.dashboardFuelIcon())
                                .setBrowsable(true)
                                .setOnClickListener {
                                    screenManager.push(AutoFuelForecastScreen(carContext, settingsManager, fuelForecastRepository))
                                }
                                .build()
                        )
                        .addItem(
                            Row.Builder()
                                .setTitle(carContext.getString(R.string.cd_map_settings))
                                .setImage(carContext.dashboardSettingsIcon())
                                .setBrowsable(true)
                                .setOnClickListener {
                                    screenManager.push(AutoMapSettingsScreen(carContext, settingsManager))
                                }
                                .build()
                        )
                        .addItem(
                            Row.Builder()
                                .setTitle(carContext.getString(R.string.cd_settings))
                                .setImage(carContext.carIconUntinted(R.drawable.ic_launcher_foreground))
                                .setBrowsable(true)
                                .setOnClickListener {
                                    screenManager.push(AutoSettingsScreen(carContext, settingsManager))
                                }
                                .build()
                        )
                        .build()

                    ListTemplate.Builder()
                        .setHeader(
                            Header.Builder()
                                .setTitle(carContext.getString(R.string.screen_more))
                                .setStartHeaderAction(Action.BACK)
                                .build()
                        )
                        .setSingleList(moreList)
                        .build()
                }
            }
        )
    }
}
