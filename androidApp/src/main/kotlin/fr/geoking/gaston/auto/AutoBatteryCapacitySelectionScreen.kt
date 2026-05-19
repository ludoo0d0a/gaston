package fr.geoking.gaston.auto

import fr.geoking.gaston.R
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.gaston.SettingsManager

class AutoBatteryCapacitySelectionScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    private val options = listOf(null, 40f, 50f, 60f, 70f, 80f, 90f, 100f)

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()

        options.forEach { value ->
            val isSelected = settings.batteryCapacityKwh == value
            val label = value?.let { "$it kWh" } ?: "Not set"
            val displayLabel = if (isSelected) "$label (Selected)" else label
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(displayLabel)
                    .setOnClickListener {
                        settingsManager.setBatteryCapacityKwh(value)
                        screenManager.pop()
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle(carContext.getString(R.string.screen_battery_capacity)).setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
