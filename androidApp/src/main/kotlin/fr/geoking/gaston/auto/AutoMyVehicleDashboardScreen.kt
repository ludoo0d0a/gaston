package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import fr.geoking.gaston.CarMapMode
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

        // Action 1: Find Stations
        gridBuilder.addItem(
            GridItem.Builder()
                .setTitle(carContext.getString(R.string.action_search))
                .setText(vehicleTitle)
                .setImage(carContext.dashboardMyCarIcon())
                .setOnClickListener {
                    settingsManager.setMyVehicleMode()
                    pushMapScreen(vehicleTitle)
                }
                .build()
        )

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

    private fun pushMapScreen(title: String? = null) {
        val mapDeps = getMapDeps()
        if (mapDeps != null) {
            val finalTitle = title ?: "Nearby Stations"
            val screen = when (settingsManager.settings.value.carMapMode) {
                CarMapMode.Native -> NativeMapPoiScreen(
                    carContext = carContext,
                    poiProvider = mapDeps.poiProvider,
                    availabilityProviderFactory = mapDeps.availabilityProviderFactory,
                    settingsManager = settingsManager,
                    communityRepo = mapDeps.communityRepo,
                    favoritesRepo = mapDeps.favoritesRepo,
                    title = finalTitle
                )
                CarMapMode.Custom -> CustomMapPoiScreen(
                    carContext = carContext,
                    poiProvider = mapDeps.poiProvider,
                    availabilityProviderFactory = mapDeps.availabilityProviderFactory,
                    settingsManager = settingsManager,
                    routePlanner = mapDeps.routePlanner,
                    routingClient = mapDeps.routingClient,
                    tollCalculator = mapDeps.tollCalculator,
                    trafficProviderFactory = mapDeps.trafficProviderFactory,
                    geocodingClient = mapDeps.geocodingClient,
                    communityRepo = mapDeps.communityRepo,
                    favoritesRepo = mapDeps.favoritesRepo,
                    title = finalTitle
                )
                CarMapMode.MapLibre -> MapLibrePoiScreen(
                    carContext = carContext,
                    poiProvider = mapDeps.poiProvider,
                    availabilityProviderFactory = mapDeps.availabilityProviderFactory,
                    settingsManager = settingsManager,
                    routePlanner = mapDeps.routePlanner,
                    routingClient = mapDeps.routingClient,
                    tollCalculator = mapDeps.tollCalculator,
                    trafficProviderFactory = mapDeps.trafficProviderFactory,
                    geocodingClient = mapDeps.geocodingClient,
                    communityRepo = mapDeps.communityRepo,
                    favoritesRepo = mapDeps.favoritesRepo,
                    title = finalTitle
                )
            }
            screenManager.push(screen)
        }
    }
}
