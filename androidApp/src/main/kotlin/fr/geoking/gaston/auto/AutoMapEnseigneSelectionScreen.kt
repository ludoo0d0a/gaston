package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.ui.MAP_ENSEIGNE_OPTIONS

class AutoMapEnseigneSelectionScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()

        MAP_ENSEIGNE_OPTIONS.forEach { (id, label) ->
            val isSelected = settings.mapEnseigneType == id
            val displayLabel = if (isSelected) "$label (Selected)" else label
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(displayLabel)
                    .setOnClickListener {
                        settingsManager.setMapEnseigneType(id)
                        screenManager.pop()
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle("Enseigne").setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
