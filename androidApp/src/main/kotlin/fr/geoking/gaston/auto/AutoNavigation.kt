package fr.geoking.gaston.auto

import androidx.car.app.AppManager
import androidx.car.app.CarToast
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

/** Cycles [CarMapMode] and replaces the current map screen with the one matching the new mode. */
fun Screen.swapMapMode(
    settingsManager: SettingsManager,
    mapDeps: MapDeps,
    title: String? = null
) {
    val newMode = settingsManager.settings.value.carMapMode.next()
    settingsManager.setCarMapMode(newMode)
    carContext.getCarService(AppManager::class.java)
        .showToast(newMode.displayLabel(carContext), CarToast.LENGTH_SHORT)
    screenManager.pop()
    pushMapScreen(settingsManager, mapDeps, title)
}
