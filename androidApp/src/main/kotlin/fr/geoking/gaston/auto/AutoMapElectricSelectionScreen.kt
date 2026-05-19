package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager

class AutoMapElectricSelectionScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.min_power))
                .addText(if (settings.mapPowerLevels.isEmpty()) carContext.getString(R.string.all) else settings.mapPowerLevels.joinToString(", ") { "${it}kW" })
                .setOnClickListener {
                    screenManager.push(AutoMapIrvePowerSelectionScreen(carContext, settingsManager))
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.connectors))
                .addText(if (settings.selectedMapConnectorTypes.isEmpty()) carContext.getString(R.string.all) else settings.selectedMapConnectorTypes.joinToString(", "))
                .setOnClickListener {
                    screenManager.push(AutoMapConnectorSelectionScreen(carContext, settingsManager))
                }
                .build()
        )

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle(carContext.getString(R.string.electric_settings)).setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
