package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.ThemeMode

class AutoThemeSelectionScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()

        ThemeMode.entries.forEach { mode ->
            val isSelected = settings.uiThemeMode == mode
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(mode.name)
                    .addText(if (isSelected) carContext.getString(fr.geoking.gaston.R.string.selected) else "")
                    .setOnClickListener {
                        settingsManager.saveSettings(settings.copy(uiThemeMode = mode))
                        invalidate()
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle("App theme")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }
}
