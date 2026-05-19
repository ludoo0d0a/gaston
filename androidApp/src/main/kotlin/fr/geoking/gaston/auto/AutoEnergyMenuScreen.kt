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
                .addText(if (isFuelMode) "Selected: ${fuels.joinToString(", ")}" else "Tap to select fuel types")
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
                .addText(if (isElectricMode) "Selected: Power and connectors" else "Tap for EV settings")
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
                .setTitle("Hybrid")
                .addText(if (isHybridMode) "Selected: Fuel + Electric" else "Fuel + Electric")
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
                    .setTitle("Energy")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }
}
