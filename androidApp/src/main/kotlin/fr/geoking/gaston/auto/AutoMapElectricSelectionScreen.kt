package fr.geoking.gaston.auto

import fr.geoking.gaston.R
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.gaston.SettingsManager

class AutoMapElectricSelectionScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val isSwapSelected = settings.selectedMapEnergyTypes.contains("swap")
        val listBuilder = ItemList.Builder()

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Battery Swap")
                .setToggle(
                    Toggle.Builder { checked ->
                        if (checked) {
                            settingsManager.saveSettings(settings.copy(
                                selectedMapEnergyTypes = setOf("swap"),
                                mapPowerLevels = emptySet(),
                                selectedMapConnectorTypes = emptySet()
                            ))
                        } else {
                            settingsManager.saveSettings(settings.copy(selectedMapEnergyTypes = emptySet()))
                        }
                        invalidate()
                    }.setChecked(isSwapSelected).build()
                )
                .build()
        )

        if (isSwapSelected) {
            return ListTemplate.Builder()
                .setSingleList(listBuilder.build())
                .setHeader(Header.Builder().setTitle(carContext.getString(R.string.screen_electric_settings)).setStartHeaderAction(Action.BACK).build())
                .addAction(Action.Builder()
                    .setTitle(carContext.getString(R.string.action_disable_swap))
                    .setOnClickListener {
                        settingsManager.saveSettings(settings.copy(selectedMapEnergyTypes = settings.selectedMapEnergyTypes - "swap"))
                        invalidate()
                    }
                    .build())
                .build()
        }

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.screen_min_power))
                .addText(
                    if (settings.mapPowerLevels.isEmpty()) carContext.getString(R.string.filter_any)
                    else settings.mapPowerLevels.joinToString(", ") {
                        carContext.getString(R.string.power_kw_any, it)
                    }
                )
                .setOnClickListener {
                    screenManager.push(AutoMapIrvePowerSelectionScreen(carContext, settingsManager))
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.screen_connectors))
                .addText(
                    if (settings.selectedMapConnectorTypes.isEmpty()) carContext.getString(R.string.filter_any)
                    else settings.selectedMapConnectorTypes.joinToString(", ")
                )
                .setOnClickListener {
                    screenManager.push(AutoMapConnectorSelectionScreen(carContext, settingsManager))
                }
                .build()
        )

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle(carContext.getString(R.string.screen_electric_settings)).setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
