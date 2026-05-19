package fr.geoking.gaston.auto

import fr.geoking.gaston.R
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.gaston.SettingsManager

class AutoEvRangeSelectionScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    private val rangeOptions = listOf(200, 300, 400, 500, 600, 800)

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()

        rangeOptions.forEach { km ->
            val isSelected = settings.evRangeKm == km
            val label = "$km km"
            val displayLabel = if (isSelected) "$label (Selected)" else label
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(displayLabel)
                    .setOnClickListener {
                        settingsManager.setEvRangeKm(km)
                        screenManager.pop()
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle(carContext.getString(R.string.screen_range)).setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
