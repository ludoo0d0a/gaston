package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.compose.ui.graphics.toArgb
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.di.MapDeps
import fr.geoking.gaston.poi.EnergyFilterMode
import fr.geoking.gaston.auto.mapsforge.MapsforgePoiScreen
import fr.geoking.gaston.ui.ColorHelper
import fr.geoking.gaston.ui.MAP_IRVE_POWER_OPTIONS

class AutoEvDashboardScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager,
    private val getMapDeps: () -> MapDeps?
) : Screen(carContext) {

    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoEvDashboardScreen") {
        val gridBuilder = ItemList.Builder()

        MAP_IRVE_POWER_OPTIONS.forEach { (kw, label) ->
            val levelColor = ColorHelper.getPowerColorByLevel(kw)
            val argb = levelColor.toArgb()
            val carColor = CarColor.createCustom(argb, argb)

            gridBuilder.addItem(
                GridItem.Builder()
                    .setTitle(label)
                    .setImage(carContext.carIcon(R.drawable.ic_poi_electric, carColor))
                    .setOnClickListener {
                        settingsManager.setEnergyFilterMode(EnergyFilterMode.Electric)
                        settingsManager.setMapPowerLevels(setOf(kw))
                        pushMapScreen(label)
                    }
                    .build()
            )
        }

        GridTemplate.Builder()
            .setSingleList(gridBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.search_mode_ev))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }

    private fun pushMapScreen(title: String? = null) {
        val mapDeps = getMapDeps() ?: return
        val finalTitle = title ?: carContext.getString(R.string.dashboard_nearby_stations)
        screenManager.pop()
        screenManager.push(
            AutoMapScreenFactory.createMapPoiScreen(
                carContext = carContext,
                mapDeps = mapDeps,
                settingsManager = settingsManager,
                title = finalTitle,
            )
        )
    }
}
