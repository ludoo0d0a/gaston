package fr.geoking.gaston.auto

import fr.geoking.gaston.R
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.ui.MAP_CONNECTOR_OPTIONS

class AutoMapConnectorSelectionScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()

        MAP_CONNECTOR_OPTIONS.forEach { (id, label) ->
            val isSelected = settings.selectedMapConnectorTypes.contains(id)
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(label)
                    .addText(if (isSelected) "Enabled" else "Disabled")
                    .setToggle(
                        Toggle.Builder { checked ->
                            val current = settingsManager.settings.value.selectedMapConnectorTypes
                            val next = if (checked) current + id else current - id
                            if (checked && settings.selectedMapEnergyTypes.contains("swap")) {
                                settingsManager.saveSettings(settings.copy(
                                    selectedMapEnergyTypes = settings.selectedMapEnergyTypes - "swap",
                                    selectedMapConnectorTypes = next
                                ))
                            } else {
                                settingsManager.setMapConnectorTypes(next)
                            }
                            invalidate()
                        }.setChecked(isSelected).build()
                    )
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle(carContext.getString(R.string.screen_connectors)).setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
