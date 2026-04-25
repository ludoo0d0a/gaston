package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.gaston.SettingsManager

class AutoEvConsumptionSelectionScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    private val consumptionOptions = listOf(null, 15f, 18f, 20f, 22f, 25f)

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()

        consumptionOptions.forEach { value ->
            val isSelected = settings.evConsumptionKwhPer100km == value
            val label = value?.let { "$it kWh/100km" } ?: "Not set"
            val displayLabel = if (isSelected) "$label (Selected)" else label
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(displayLabel)
                    .setOnClickListener {
                        settingsManager.setEvConsumptionKwhPer100km(value)
                        screenManager.pop()
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle("Consumption").setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
