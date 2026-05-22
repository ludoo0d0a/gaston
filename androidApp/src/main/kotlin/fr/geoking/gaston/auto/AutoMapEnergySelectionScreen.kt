package fr.geoking.gaston.auto

import fr.geoking.gaston.R
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.effectiveMapEnergyFilterIds
import fr.geoking.gaston.ui.MAP_ENERGY_OPTIONS

class AutoMapEnergySelectionScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()
        val effectiveEnergies = settings.effectiveMapEnergyFilterIds()
        MAP_ENERGY_OPTIONS.filter { it.first != "electric" }.take(6).forEach { (id, label) ->
            val isSelected = effectiveEnergies.contains(id)
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(label)
                    .setToggle(
                        Toggle.Builder { checked ->
                            val isHybrid = settings.selectedMapEnergyTypes.contains("electric")
                            val next = if (checked) {
                                if (isHybrid) setOf(id, "electric") else setOf(id)
                            } else {
                                if (isHybrid) setOf("electric") else emptySet()
                            }
                            settingsManager.setMapEnergyTypes(next)
                            invalidate()
                        }.setChecked(isSelected).build()
                    )
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle(carContext.getString(R.string.screen_energy_types)).setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
