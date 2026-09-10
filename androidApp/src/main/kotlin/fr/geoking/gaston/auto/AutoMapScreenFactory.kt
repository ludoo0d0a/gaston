package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import fr.geoking.gaston.CarMapMode
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.di.MapDeps

/** Creates the Android Auto map [Screen] for the current [CarMapMode]. */
object AutoMapScreenFactory {

    fun createMapPoiScreen(
        carContext: CarContext,
        mapDeps: MapDeps,
        settingsManager: SettingsManager,
        title: String = carContext.getString(R.string.dashboard_nearby_stations),
    ): Screen = when (settingsManager.settings.value.carMapMode) {
        CarMapMode.Native -> NativeMapPoiScreen(
            carContext = carContext,
            poiProvider = mapDeps.poiProvider,
            availabilityProviderFactory = mapDeps.availabilityProviderFactory,
            settingsManager = settingsManager,
            communityRepo = mapDeps.communityRepo,
            favoritesRepo = mapDeps.favoritesRepo,
            title = title,
            mapDeps = mapDeps,
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
            title = title,
            mapDeps = mapDeps,
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
            title = title,
            canvasMapModeConfig = CanvasMapModeConfig.mapLibre(carContext),
            mapDeps = mapDeps,
        )
        CarMapMode.MapTiler -> MapLibrePoiScreen(
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
            title = title,
            canvasMapModeConfig = CanvasMapModeConfig.mapTiler(carContext),
            mapDeps = mapDeps,
        )
        CarMapMode.Protomaps -> MapLibrePoiScreen(
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
            title = title,
            canvasMapModeConfig = CanvasMapModeConfig.protomaps(carContext),
            mapDeps = mapDeps,
        )
        CarMapMode.Mapsforge -> fr.geoking.gaston.auto.mapsforge.MapsforgePoiScreen(
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
            title = title,
            mapDeps = mapDeps,
        )
    }
}
