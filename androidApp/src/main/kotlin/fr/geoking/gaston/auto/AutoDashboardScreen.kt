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
import fr.geoking.gaston.PoiProviderSelectionMode
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.poi.PoiProviderType
import fr.geoking.gaston.di.MapDeps
import fr.geoking.gaston.repository.FuelForecastRepository
import fr.geoking.gaston.shared.network.NetworkService

class AutoDashboardScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager,
    private val networkService: NetworkService,
    private val fuelForecastRepository: FuelForecastRepository,
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

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Search")
                .addText("Search for gas or EV stations by name/brand")
                .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_search)).build())
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

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Fuel price outlook")
                .addText("Local estimate from market + nearby pumps")
                .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_map)).build())
                .setOnClickListener {
                    screenManager.push(
                        AutoFuelForecastScreen(carContext, settingsManager, fuelForecastRepository)
                    )
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Map")
                .addText("Nearby gas/EV stations")
                .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_map)).build())
                .setOnClickListener {
                    settingsManager.setUseVehicleFilter(false)
                    settingsManager.setPoiProviderSelectionMode(PoiProviderSelectionMode.Auto)
                    if (settingsManager.settings.value.selectedOverpassAmenityTypes == setOf("parking")) {
                        settingsManager.setOverpassAmenityTypes(emptySet())
                    }
                    pushMapScreen()
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Routes")
                .addText("Plan your journey")
                .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_swap_horiz)).build())
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
                .setTitle("More Options")
                .addText("Settings, Network, Lab, Parkings")
                .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_settings)).build())
                .setOnClickListener {
                    screenManager.push(
                        object : Screen(carContext) {
                            override fun onGetTemplate(): Template {
                                val moreList = ItemList.Builder()
                                    .addItem(
                                        Row.Builder()
                                            .setTitle("POI Map (Vehicle)")
                                            .addText("Filtered by vehicle settings")
                                            .setOnClickListener {
                                                settingsManager.setUseVehicleFilter(true)
                                                settingsManager.setPoiProviderSelectionMode(PoiProviderSelectionMode.Auto)
                                                if (settingsManager.settings.value.selectedOverpassAmenityTypes == setOf("parking")) {
                                                    settingsManager.setOverpassAmenityTypes(emptySet())
                                                }
                                                pushMapScreen()
                                                screenManager.pop()
                                            }
                                            .build()
                                    )
                                    .addItem(
                                        Row.Builder()
                                            .setTitle("Parkings")
                                            .addText("Nearby lots")
                                            .setOnClickListener {
                                                settingsManager.setUseVehicleFilter(false)
                                                settingsManager.setPoiProviderSelectionMode(PoiProviderSelectionMode.Manual)
                                                settingsManager.setPoiProviderTypes(setOf(PoiProviderType.Overpass))
                                                settingsManager.setOverpassAmenityTypes(setOf("parking"))
                                                pushMapScreen()
                                                screenManager.pop()
                                            }
                                            .build()
                                    )
                                    .addItem(
                                        Row.Builder()
                                            .setTitle("Network & Location")
                                            .setOnClickListener { screenManager.push(AutoNetworkLocationInfoScreen(carContext, networkService)) }
                                            .build()
                                    )
                                    .addItem(
                                        Row.Builder()
                                            .setTitle("Template lab")
                                            .setOnClickListener { screenManager.push(AutoTemplateLabScreen(carContext, settingsManager, getMapDeps)) }
                                            .build()
                                    )
                                    .addItem(
                                        Row.Builder()
                                            .setTitle("Settings")
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

        val title = if (settingsManager.settings.value.isPremium) "Gaston Premium" else "Gaston"
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

    private fun pushMapScreen() {
        val mapDeps = getMapDeps()
        if (mapDeps != null) {
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
}
