package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import fr.geoking.gaston.CarMapMode
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.auto.mapsforge.MapsforgePoiScreen
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
        CarMapMode.Raster -> CustomMapPoiScreen(
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
        CarMapMode.MapLibreMapSnapshotter ->
            mapLibreScreen(carContext, mapDeps, settingsManager, title, CanvasMapModeConfig.mapLibreSnapshotter(carContext))
        CarMapMode.MapLibreVirtualDisplay ->
            mapLibreScreen(carContext, mapDeps, settingsManager, title, CanvasMapModeConfig.mapLibreVirtualDisplay(carContext))
        // TODO(MapLibreEglDirect): native EGL pipeline not implemented; use the VirtualDisplay path meanwhile.
        CarMapMode.MapLibreEglDirect ->
            mapLibreScreen(carContext, mapDeps, settingsManager, title, CanvasMapModeConfig.mapLibreVirtualDisplay(carContext))
        CarMapMode.Mapbox ->
            mapLibreScreen(carContext, mapDeps, settingsManager, title, CanvasMapModeConfig.mapbox(carContext))
        CarMapMode.Mapsforge -> MapsforgePoiScreen(
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

    private fun mapLibreScreen(
        carContext: CarContext,
        mapDeps: MapDeps,
        settingsManager: SettingsManager,
        title: String,
        config: CanvasMapModeConfig,
    ): Screen = MapLibrePoiScreen(
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
        canvasMapModeConfig = config,
        mapDeps = mapDeps,
    )
}
