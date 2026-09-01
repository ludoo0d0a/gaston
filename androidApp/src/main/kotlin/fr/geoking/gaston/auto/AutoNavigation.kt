package fr.geoking.gaston.auto

import androidx.car.app.Screen
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
    screenManager.push(
        AutoMapScreenFactory.createMapPoiScreen(
            carContext = carContext,
            mapDeps = mapDeps,
            settingsManager = settingsManager,
            title = finalTitle,
        )
    )
}
