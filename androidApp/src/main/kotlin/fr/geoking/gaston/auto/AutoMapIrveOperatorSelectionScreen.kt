package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.effectiveIrveOperatorFilter
import fr.geoking.gaston.ui.BrandHelper

class AutoMapIrveOperatorSelectionScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()
        val operators = BrandHelper.getElectricBrands()
        val effectiveOperators = settings.effectiveIrveOperatorFilter()

        operators.forEach { (id, label) ->
            val isSelected = effectiveOperators.contains(id)
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(label)
                    .addText(if (isSelected) "Active" else "Inactive")
                    .setOnClickListener {
                        val newOps = if (settings.mapIrveOperators.contains(id)) settings.mapIrveOperators - id else settings.mapIrveOperators + id
                        settingsManager.setMapIrveOperators(newOps)
                        invalidate()
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle("Opérateur").setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
