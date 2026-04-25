package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import fr.geoking.gaston.CarMapMode
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.di.MapDeps
import fr.geoking.gaston.repository.FuelForecastRepository
import fr.geoking.gaston.shared.network.NetworkService

/**
 * Android Auto entry for the **playstore** flavor: POI category, no voice assistant entry.
 * Use [AutoTemplateLabScreen] to exercise Car App Library templates (POI vs navigation style).
 */
class AutoPlaystoreDashboardScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager,
    private val networkService: NetworkService,
    private val fuelForecastRepository: FuelForecastRepository,
    private val getMapDeps: () -> MapDeps?
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        fun gridIcon(drawableId: Int) =
            CarIcon.Builder(IconCompat.createWithResource(carContext, drawableId)).build()

        val grid = ItemList.Builder()

        grid.addItem(
            GridItem.Builder()
                .setTitle("Search")
                .setText("Name or brand")
                .setImage(gridIcon(R.drawable.ic_search))
                .setOnClickListener {
                    val mapDeps = getMapDeps()
                    if (mapDeps != null) {
                        screenManager.push(
                            AutoPoiSearchScreen(
                                carContext = carContext,
                                poiProvider = mapDeps.poiProvider,
                                settingsManager = settingsManager,
                                availabilityProviderFactory = mapDeps.availabilityProviderFactory
                            )
                        )
                    }
                }
                .build()
        )

        grid.addItem(
            GridItem.Builder()
                .setTitle("Map")
                .setText("All filters")
                .setImage(gridIcon(R.drawable.ic_map))
                .setOnClickListener {
                    settingsManager.setUseVehicleFilter(false)
                    pushMapScreen()
                }
                .build()
        )

        grid.addItem(
            GridItem.Builder()
                .setTitle("POI (vehicle)")
                .setText("Saved vehicle filters")
                .setImage(gridIcon(R.drawable.ic_poi_caravan_rounded))
                .setOnClickListener {
                    settingsManager.setUseVehicleFilter(true)
                    pushMapScreen()
                }
                .build()
        )

        grid.addItem(
            GridItem.Builder()
                .setTitle("Fuel outlook")
                .setText("Next days estimate")
                .setImage(gridIcon(R.drawable.ic_poi_gas_rounded))
                .setOnClickListener {
                    screenManager.push(
                        AutoFuelForecastScreen(carContext, settingsManager, fuelForecastRepository)
                    )
                }
                .build()
        )

        grid.addItem(
            GridItem.Builder()
                .setTitle("Routes")
                .setText("Plan journey")
                .setImage(gridIcon(R.drawable.ic_swap_horiz))
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

        grid.addItem(
            GridItem.Builder()
                .setTitle("Template lab")
                .setText("Try templates & maps")
                .setImage(gridIcon(R.drawable.ic_launcher))
                .setOnClickListener {
                    screenManager.push(AutoTemplateLabScreen(carContext, settingsManager, getMapDeps))
                }
                .build()
        )

        grid.addItem(
            GridItem.Builder()
                .setTitle("Network & GPS")
                .setText("Diagnostics")
                .setImage(gridIcon(R.drawable.ic_poi_radar_rounded))
                .setOnClickListener {
                    screenManager.push(AutoNetworkLocationInfoScreen(carContext, networkService))
                }
                .build()
        )

        grid.addItem(
            GridItem.Builder()
                .setTitle("Map settings")
                .setText("Sources & vehicle")
                .setImage(gridIcon(R.drawable.ic_settings))
                .setOnClickListener {
                    screenManager.push(AutoMapSettingsScreen(carContext, settingsManager))
                }
                .build()
        )

        return GridTemplate.Builder()
            .setSingleList(grid.build())
            .setHeader(
                Header.Builder()
                    .setTitle("Julius - station finder")
                    .setStartHeaderAction(Action.APP_ICON)
                    .build()
            )
            .build()
    }

    private fun pushMapScreen() {
        val mapDeps = getMapDeps() ?: return
        val screen = if (settingsManager.settings.value.carMapMode == CarMapMode.Native) {
            NativeMapPoiScreen(
                carContext = carContext,
                poiProvider = mapDeps.poiProvider,
                availabilityProviderFactory = mapDeps.availabilityProviderFactory,
                settingsManager = settingsManager,
                communityRepo = mapDeps.communityRepo,
                favoritesRepo = mapDeps.favoritesRepo
            )
        } else {
            CustomMapPoiScreen(
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
                favoritesRepo = mapDeps.favoritesRepo
            )
        }
        screenManager.push(screen)
    }
}
