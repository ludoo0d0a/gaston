package fr.geoking.gaston.auto

import fr.geoking.gaston.R
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.gaston.SettingsManager

class AutoGasTankCapacitySelectionScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    private val options = listOf(null, 30f, 40f, 50f, 60f, 70f, 80f, 100f)

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()

        options.forEach { value ->
            val isSelected = settings.gasTankCapacityLiters == value
            val label = value?.let { "$it L" } ?: "Not set"
            val displayLabel = if (isSelected) "$label (Selected)" else label
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(displayLabel)
                    .setOnClickListener {
                        settingsManager.setGasTankCapacityLiters(value)
                        screenManager.pop()
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle(carContext.getString(R.string.screen_tank_capacity)).setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
