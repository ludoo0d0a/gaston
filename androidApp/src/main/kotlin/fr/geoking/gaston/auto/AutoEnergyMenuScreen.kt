package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager

class AutoEnergyMenuScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val types = settings.selectedMapEnergyTypes
        val hasElectric = types.contains("electric")
        val fuels = types - "electric"
        val hasFuel = fuels.isNotEmpty()

        val isElectricMode = hasElectric && !hasFuel
        val isFuelMode = !hasElectric && hasFuel
        val isHybridMode = hasElectric && hasFuel

        val listBuilder = ItemList.Builder()

        // Fuel Row
        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.search_mode_fuel))
                .addText(if (isFuelMode) carContext.getString(R.string.selected_prefix, fuels.joinToString(", ")) else carContext.getString(R.string.tap_to_select_fuel))
                .setOnClickListener {
                    if (!hasFuel) {
                        settingsManager.setMapEnergyTypes(setOf("sp95"))
                    } else if (hasElectric) {
                        settingsManager.setMapEnergyTypes(fuels)
                    }
                    screenManager.push(AutoMapEnergySelectionScreen(carContext, settingsManager))
                }
                .build()
        )

        // Electric Row
        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.search_mode_ev))
                .addText(if (isElectricMode) carContext.getString(R.string.selected_ev_settings) else carContext.getString(R.string.tap_for_ev_settings))
                .setOnClickListener {
                    if (!hasElectric) {
                        settingsManager.setMapEnergyTypes(setOf("electric"))
                    } else if (hasFuel) {
                        settingsManager.setMapEnergyTypes(setOf("electric"))
                    }
                    screenManager.push(AutoMapElectricSelectionScreen(carContext, settingsManager))
                }
                .build()
        )

        // Hybrid Row
        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.energy_hybrid))
                .addText(if (isHybridMode) carContext.getString(R.string.selected_prefix, carContext.getString(R.string.energy_hybrid)) else carContext.getString(R.string.fuel_and_electric))
                .setOnClickListener {
                    val nextFuels = if (fuels.isEmpty()) setOf("sp95") else fuels
                    settingsManager.setMapEnergyTypes(nextFuels + "electric")
                    invalidate()
                }
                .build()
        )

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.energy))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }
}
