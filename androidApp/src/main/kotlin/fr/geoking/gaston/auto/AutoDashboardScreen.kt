package fr.geoking.gaston.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import fr.geoking.gaston.CarMapMode
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.poi.EnergyFilterMode
import fr.geoking.gaston.poi.PoiProviderType
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

    init {
        val screenNames = listOf(
            "AutoFuelForecastScreen",
            "AutoDashboardScreen",
            "NativeMapPoiScreen",
            "CustomMapPoiScreen",
            "AutoRoutePlanningScreen",
            "AutoNetworkLocationInfoScreen",
            "AutoSettingsScreen",
            "AutoTemplateLabScreen",
        )
        Log.d("gastonNavigation", "Android Auto Screens: ${screenNames.joinToString(", ")}")
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        val fuelTitle = carContext.getString(R.string.search_mode_fuel)
        listBuilder.addItem(
            Row.Builder()
                .setTitle(fuelTitle)
                .setImage(carContext.dashboardFuelIcon())
                .setOnClickListener {
                    settingsManager.setEnergyFilterMode(EnergyFilterMode.Fuel)
                    pushMapScreen(fuelTitle)
                }
                .build()
        )

        val evTitle = carContext.getString(R.string.search_mode_ev)
        listBuilder.addItem(
            Row.Builder()
                .setTitle(evTitle)
                .setImage(carContext.dashboardEvIcon())
                .setOnClickListener {
                    settingsManager.setEnergyFilterMode(EnergyFilterMode.Electric)
                    pushMapScreen(evTitle)
                }
                .build()
        )

        val myCarTitle = carContext.getString(R.string.search_mode_my_car)
        listBuilder.addItem(
            Row.Builder()
                .setTitle(myCarTitle)
                .setImage(carContext.dashboardMyCarIcon())
                .setOnClickListener {
                    settingsManager.setMyCarMode()
                    pushMapScreen(myCarTitle)
                }
                .build()
        )

        val otherTitle = carContext.getString(R.string.search_mode_other)
        listBuilder.addItem(
            Row.Builder()
                .setTitle(otherTitle)
                .setImage(carContext.dashboardOtherIcon())
                .setOnClickListener {
                    settingsManager.setOtherMode()
                    pushMapScreen(otherTitle)
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.dashboard_routes))
                .setImage(carContext.dashboardRoutesIcon())
                .setOnClickListener {
                    val mapDeps = getMapDeps()
                    if (mapDeps != null) {
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
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.dashboard_network))
                .setImage(carContext.dashboardNetworkIcon())
                .setOnClickListener { screenManager.push(AutoNetworkLocationInfoScreen(carContext, networkService)) }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Emergency")
                .setImage(carContext.dashboardEmergencyIcon())
                .setOnClickListener {
                    screenManager.push(AutoEmergencyScreen(carContext, networkService, connectivityManager))
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("More Options")
                .setImage(carContext.dashboardSettingsIcon())
                .setOnClickListener {
                    screenManager.push(
                        object : Screen(carContext) {
                            override fun onGetTemplate(): Template {
                                val moreList = ItemList.Builder()
                                    .addItem(
                                        Row.Builder()
                                            .setTitle("Fuel outlook")
                                            .setImage(carContext.dashboardFuelIcon())
                                            .setOnClickListener {
                                                screenManager.push(
                                                    AutoFuelForecastScreen(carContext, settingsManager, fuelForecastRepository)
                                                )
                                            }
                                            .build()
                                    )
                                    .addItem(
                                        Row.Builder()
                                            .setTitle("Template lab")
                                            .setImage(carContext.carIconUntinted(R.mipmap.ic_launcher))
                                            .setOnClickListener { screenManager.push(AutoTemplateLabScreen(carContext, settingsManager, getMapDeps)) }
                                            .build()
                                    )
                                    .addItem(
                                        Row.Builder()
                                            .setTitle("Settings")
                                            .setImage(carContext.dashboardSettingsIcon())
                                            .setOnClickListener { screenManager.push(AutoSettingsScreen(carContext, settingsManager)) }
                                            .build()
                                    )
                                    .build()

                                return ListTemplate.Builder()
                                    .setHeader(Header.Builder().setTitle("More Options").setStartHeaderAction(Action.BACK).build())
                                    .setSingleList(moreList)
                                    .build()
                            }
                        }
                    )
                }
                .build()
        )

        val title = if (settingsManager.settings.value.hasPremiumFeatures) "Gaston Premium" else "Gaston"
        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle(title)
                    .setStartHeaderAction(Action.APP_ICON)
                    .build()
            )
            .build()
    }

    private fun pushMapScreen(title: String? = null) {
        val mapDeps = getMapDeps()
        if (mapDeps != null) {
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
}
