package fr.geoking.gaston.auto

import fr.geoking.gaston.R
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
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
                .addText(if (isFuelMode) "${carContext.getString(R.string.filter_selected)}: ${fuels.joinToString(", ")}" else carContext.getString(R.string.fuel_tap_to_select))
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
                .setTitle(carContext.getString(R.string.energy_electric))
                .addText(if (isElectricMode) carContext.getString(R.string.energy_ev_all_selected) else carContext.getString(R.string.ev_tap_for_settings))
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
                .addText(if (isHybridMode) carContext.getString(R.string.energy_hybrid_selected) else carContext.getString(R.string.energy_hybrid_summary))
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
                    .setTitle(carContext.getString(R.string.energy_label))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }
}
