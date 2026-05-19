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
import fr.geoking.gaston.CarMapMode
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.poi.EnergyFilterMode
import fr.geoking.gaston.poi.PoiProviderType
import fr.geoking.gaston.di.MapDeps
import fr.geoking.gaston.repository.FuelForecastRepository
import fr.geoking.gaston.shared.location.ConnectivityManager
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
    private val connectivityManager: ConnectivityManager,
    private val getMapDeps: () -> MapDeps?
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val grid = ItemList.Builder()

        val fuelTitle = carContext.getString(R.string.search_mode_fuel)
        grid.addItem(
            GridItem.Builder()
                .setTitle(fuelTitle)
                .setImage(carContext.dashboardFuelIcon())
                .setOnClickListener {
                    settingsManager.setEnergyFilterMode(EnergyFilterMode.Fuel)
                    pushMapScreen(fuelTitle)
                }
                .build()
        )

        val evTitle = carContext.getString(R.string.search_mode_ev)
        grid.addItem(
            GridItem.Builder()
                .setTitle(evTitle)
                .setImage(carContext.dashboardEvIcon())
                .setOnClickListener {
                    settingsManager.setEnergyFilterMode(EnergyFilterMode.Electric)
                    pushMapScreen(evTitle)
                }
                .build()
        )

        val myCarTitle = carContext.getString(R.string.search_mode_my_car)
        grid.addItem(
            GridItem.Builder()
                .setTitle(myCarTitle)
                .setImage(carContext.dashboardMyCarIcon())
                .setOnClickListener {
                    settingsManager.setMyCarMode()
                    pushMapScreen(myCarTitle)
                }
                .build()
        )

        val otherTitle = carContext.getString(R.string.search_mode_other)
        grid.addItem(
            GridItem.Builder()
                .setTitle(otherTitle)
                .setImage(carContext.dashboardOtherIcon())
                .setOnClickListener {
                    settingsManager.setOtherMode()
                    pushMapScreen(otherTitle)
                }
                .build()
        )

        grid.addItem(
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

        grid.addItem(
            GridItem.Builder()
                .setTitle(carContext.getString(R.string.dashboard_network))
                .setImage(carContext.dashboardNetworkIcon())
                .setOnClickListener {
                    screenManager.push(AutoNetworkLocationInfoScreen(carContext, networkService))
                }
                .build()
        )

        grid.addItem(
            GridItem.Builder()
                .setTitle(carContext.getString(R.string.dashboard_emergency))
                .setImage(carContext.dashboardEmergencyIcon())
                .setOnClickListener {
                    screenManager.push(AutoEmergencyScreen(carContext, networkService, connectivityManager))
                }
                .build()
        )

        grid.addItem(
            GridItem.Builder()
                .setTitle(carContext.getString(R.string.screen_more))
                .setImage(carContext.dashboardSettingsIcon())
                .setOnClickListener {
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
                                            .setTitle(carContext.getString(R.string.screen_template_lab))
                                            .setImage(carContext.carIconUntinted(R.mipmap.ic_launcher))
                                            .setOnClickListener {
                                                screenManager.push(AutoTemplateLabScreen(carContext, settingsManager, getMapDeps))
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
                .build()
        )

        val title = if (settingsManager.settings.value.hasPremiumFeatures) "gaston premium" else "gaston - station finder"
        return GridTemplate.Builder()
            .setSingleList(grid.build())
            .setHeader(
                Header.Builder()
                    .setTitle(title)
                    .setStartHeaderAction(Action.APP_ICON)
                    .build()
            )
            .build()
    }

    private fun pushMapScreen(title: String? = null) {
        val mapDeps = getMapDeps() ?: return
        val finalTitle = title ?: "Nearby Stations"
        val screen = if (settingsManager.settings.value.carMapMode == CarMapMode.Native) {
            NativeMapPoiScreen(
                carContext = carContext,
                poiProvider = mapDeps.poiProvider,
                availabilityProviderFactory = mapDeps.availabilityProviderFactory,
                settingsManager = settingsManager,
                communityRepo = mapDeps.communityRepo,
                favoritesRepo = mapDeps.favoritesRepo,
                title = finalTitle
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
                favoritesRepo = mapDeps.favoritesRepo,
                title = finalTitle
            )
        }
        screenManager.push(screen)
    }
}
