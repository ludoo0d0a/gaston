package fr.geoking.gaston.auto

import androidx.car.app.Screen
import fr.geoking.gaston.R
import fr.geoking.gaston.CarMapMode
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.di.MapDeps

/**
 * Common navigation helpers for Android Auto.
 */

fun Screen.pushMapScreen(
    settingsManager: SettingsManager,
    mapDeps: MapDeps,
    title: String? = null
) {
    val finalTitle = title ?: carContext.getString(R.string.dashboard_nearby_stations)
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
