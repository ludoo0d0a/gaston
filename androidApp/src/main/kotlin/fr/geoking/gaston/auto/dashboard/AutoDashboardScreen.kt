package fr.geoking.gaston.auto.dashboard

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
import fr.geoking.gaston.BuildConfig
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.di.MapDeps
import fr.geoking.gaston.repository.FuelForecastRepository
import fr.geoking.gaston.shared.location.ConnectivityManager
import fr.geoking.gaston.shared.network.NetworkService
import fr.geoking.gaston.auto.settings.developer.AutoDeveloperScreen
import fr.geoking.gaston.auto.emergency.AutoEmergencyScreen
import fr.geoking.gaston.auto.poi.AutoFavoritesScreen
import fr.geoking.gaston.auto.settings.map.AutoMapSettingsScreen
import fr.geoking.gaston.auto.route.AutoRoutePlanningScreen
import fr.geoking.gaston.auto.settings.AutoSettingsScreen
import fr.geoking.gaston.auto.alertToggleAction
import fr.geoking.gaston.auto.dashboardEvIcon
import fr.geoking.gaston.auto.dashboardFavoritesIcon
import fr.geoking.gaston.auto.dashboardFuelIcon
import fr.geoking.gaston.auto.dashboardMyCarIcon
import fr.geoking.gaston.auto.dashboardNetworkIcon
import fr.geoking.gaston.auto.dashboardOtherIcon
import fr.geoking.gaston.auto.dashboardRadarAlertIcon
import fr.geoking.gaston.auto.dashboardRoutesIcon
import fr.geoking.gaston.auto.dashboardTrafficAlertIcon
import fr.geoking.gaston.auto.safeCarTemplate
import fr.geoking.gaston.auto.carIconUntinted
import fr.geoking.gaston.auto.dashboardEmergencyIcon
import fr.geoking.gaston.auto.dashboardMapIcon
import fr.geoking.gaston.auto.dashboardSettingsIcon
import fr.geoking.gaston.auto.dashboardWeatherAlertIcon
import fr.geoking.gaston.auto.map.AutoMapScreenFactory

/**
 * Root Android Auto hub — ≤6 grid tiles so strict hosts (CONTENT_LIMIT_TYPE_GRID min 6) show all items.
 * Primary search (Fuel / EV / Map) stays one tap; secondary tools live under Places / Vehicle / Emergency / More.
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
        val settings = settingsManager.settings.value
        val trafficAlertsOn = settings.trafficAlertEnabled
        val radarAlertsOn = settings.radarWarningEnabled
        val weatherAlertsOn = "weather" in settings.selectedOverpassAmenityTypes
        val gridBuilder = ItemList.Builder()

        // 1. Map (Voir carte with active radar / traffic / weather options)
        gridBuilder.addItem(
            GridItem.Builder()
                .setTitle(carContext.getString(R.string.action_open_map))
                .setImage(carContext.dashboardMapIcon())
                .setOnClickListener {
                    val mapDeps = getMapDeps() ?: return@setOnClickListener
                    screenManager.push(
                        AutoMapScreenFactory.createMapPoiScreen(carContext, mapDeps, settingsManager)
                    )
                }
                .build()
        )

        // 2. Fuel
        gridBuilder.addItem(
            GridItem.Builder()
                .setTitle(carContext.getString(R.string.search_mode_fuel))
                .setImage(carContext.dashboardFuelIcon())
                .setOnClickListener {
                    screenManager.push(AutoFuelDashboardScreen(carContext, settingsManager, getMapDeps))
                }
                .build()
        )

        // 3. EV
        gridBuilder.addItem(
            GridItem.Builder()
                .setTitle(carContext.getString(R.string.search_mode_ev))
                .setImage(carContext.dashboardEvIcon())
                .setOnClickListener {
                    screenManager.push(AutoEvDashboardScreen(carContext, settingsManager, getMapDeps))
                }
                .build()
        )

        // 4. Places (amenities + danger zones)
        gridBuilder.addItem(
            GridItem.Builder()
                .setTitle(carContext.getString(R.string.search_mode_other))
                .setImage(carContext.dashboardOtherIcon())
                .setOnClickListener {
                    screenManager.push(AutoOtherDashboardScreen(carContext, settingsManager, getMapDeps))
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

        // 6. More (emergency, favorites, routes, network, settings…)
        gridBuilder.addItem(
            GridItem.Builder()
                .setTitle(carContext.getString(R.string.screen_more))
                .setImage(carContext.dashboardSettingsIcon())
                .setOnClickListener {
                    pushMoreOptionsScreen()
                }
                .build()
        )

        // Header end actions (max 2):
        // 1. Weather toggle
        // 2. Traffic or Radar toggle depending on radar state (or radar toggle if off)
        val appTitle = if (settings.hasPremiumFeatures) {
            carContext.getString(R.string.premium_brand_title)
        } else {
            carContext.getString(R.string.app_name)
        }
        val headerBuilder = Header.Builder()
            .setTitle(appTitle)
            .setStartHeaderAction(Action.APP_ICON)
            .addEndHeaderAction(
                carContext.alertToggleAction(
                    enabled = weatherAlertsOn,
                    icon = carContext.dashboardWeatherAlertIcon(weatherAlertsOn),
                ) {
                    if (weatherAlertsOn) {
                        settingsManager.setOverpassAmenityTypes(settings.selectedOverpassAmenityTypes - "weather")
                    } else {
                        settingsManager.setOtherMode("weather")
                    }
                    invalidate()
                },
            )
            .addEndHeaderAction(
                carContext.alertToggleAction(
                    enabled = radarAlertsOn,
                    icon = carContext.dashboardRadarAlertIcon(radarAlertsOn),
                ) {
                    settingsManager.setRadarWarningEnabled(!radarAlertsOn)
                    invalidate()
                },
            )

        GridTemplate.Builder()
            .setSingleList(gridBuilder.build())
            .setHeader(headerBuilder.build())
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
                    val moreListBuilder = ItemList.Builder()
                        .addItem(
                            Row.Builder()
                                .setTitle(carContext.getString(R.string.dashboard_emergency))
                                .setImage(carContext.dashboardEmergencyIcon())
                                .setBrowsable(true)
                                .setOnClickListener {
                                    screenManager.push(AutoEmergencyScreen(carContext, networkService, connectivityManager))
                                }
                                .build()
                        )
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
                                .setTitle(carContext.getString(R.string.cd_settings))
                                .setImage(carContext.carIconUntinted(R.drawable.ic_launcher_foreground))
                                .setBrowsable(true)
                                .setOnClickListener {
                                    screenManager.push(AutoSettingsScreen(carContext, settingsManager))
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

                    if (BuildConfig.DEBUG_DEV) {
                        moreListBuilder.addItem(
                            Row.Builder()
                                .setTitle(carContext.getString(R.string.screen_developer))
                                .setImage(carContext.dashboardSettingsIcon())
                                .setBrowsable(true)
                                .setOnClickListener {
                                    screenManager.push(AutoDeveloperScreen(carContext, settingsManager))
                                }
                                .build()
                        )
                    }

                    ListTemplate.Builder()
                        .setHeader(
                            Header.Builder()
                                .setTitle(carContext.getString(R.string.screen_more))
                                .setStartHeaderAction(Action.BACK)
                                .build()
                        )
                        .setSingleList(moreListBuilder.build())
                        .build()
                }
            }
        )
    }
}
