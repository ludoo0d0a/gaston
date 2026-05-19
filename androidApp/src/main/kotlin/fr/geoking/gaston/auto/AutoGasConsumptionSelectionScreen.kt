package fr.geoking.gaston.auto

import fr.geoking.gaston.R
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.gaston.SettingsManager

class AutoGasConsumptionSelectionScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    private val options = listOf(null, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 12f)

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()

        options.forEach { value ->
            val isSelected = settings.gasConsumptionLper100km == value
            val label = value?.let { "$it L/100km" } ?: "Not set"
            val displayLabel = if (isSelected) "$label (Selected)" else label
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(displayLabel)
                    .setOnClickListener {
                        settingsManager.setGasConsumptionLper100km(value)
                        screenManager.pop()
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle(carContext.getString(R.string.screen_fuel_consumption)).setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
