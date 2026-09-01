package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import fr.geoking.gaston.CarMapMode
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager

class AutoMapModePickerScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager,
) : Screen(carContext) {

    override fun onGetTemplate(): Template = safeCarTemplate(
        carContext = carContext,
        logTag = "AutoMapModePickerScreen",
        templateName = "ListTemplate",
    ) {
        val current = settingsManager.settings.value.carMapMode
        val listBuilder = ItemList.Builder()
        CarMapMode.entries.forEach { mode ->
            val selected = mode == current
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(mode.displayLabel(carContext))
                    .addText(mode.displayDescription(carContext))
                    .setBrowsable(false)
                    .setOnClickListener {
                        settingsManager.setCarMapMode(mode)
                        screenManager.pop()
                    }
                    .build()
            )
        }
        ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.settings_map_mode))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }
}
