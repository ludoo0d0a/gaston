package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.effectiveFuelBrandFilterIds
import fr.geoking.gaston.ui.BrandHelper

class AutoMapBrandSelectionScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()
        val brands = BrandHelper.getGasBrands()
        val effectiveBrands = settings.effectiveFuelBrandFilterIds()

        brands.forEach { (id, label) ->
            val isSelected = effectiveBrands.contains(id)
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(label)
                    .addText(if (isSelected) "Active" else "Inactive")
                    .setOnClickListener {
                        val current = settingsManager.settings.value.mapBrands
                        val next = if (isSelected) current - id else current + id
                        settingsManager.setMapBrands(next)
                        invalidate()
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle("Brands").setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
