package fr.geoking.gaston.auto

import fr.geoking.gaston.R
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.effectiveIrvePowerLevels
import fr.geoking.gaston.ui.MAP_IRVE_POWER_OPTIONS

class AutoMapIrvePowerSelectionScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()
        val effectivePowerLevels = settings.effectiveIrvePowerLevels()

        MAP_IRVE_POWER_OPTIONS.forEach { (kw, label) ->
            val isSelected = effectivePowerLevels.contains(kw)
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(label)
                    .setToggle(
                        Toggle.Builder { checked ->
                            val newLevels = if (checked) settings.mapPowerLevels + kw else settings.mapPowerLevels - kw
                            settingsManager.setMapPowerLevels(newLevels)
                            invalidate()
                        }.setChecked(isSelected).build()
                    )
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle(carContext.getString(R.string.screen_min_power)).setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
