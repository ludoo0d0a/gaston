package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.poi.PoiProviderType
import fr.geoking.gaston.poi.anyProvidesElectric

class AutoAdvancedFiltersScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()
        val providers = settings.selectedPoiProviders

        val hasElec = providers.anyProvidesElectric()
        if (hasElec) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Operators")
                    .addText(if (settings.mapIrveOperators.isEmpty()) "All" else settings.mapIrveOperators.joinToString(", "))
                    .setOnClickListener {
                        screenManager.push(AutoMapIrveOperatorSelectionScreen(carContext, settingsManager))
                    }
                    .build()
            )

            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Power Range")
                    .addText(if (settings.mapPowerLevels.isEmpty()) "All" else settings.mapPowerLevels.joinToString(", ") { "${it}kW+" })
                    .setOnClickListener {
                        screenManager.push(AutoMapIrvePowerSelectionScreen(carContext, settingsManager))
                    }
                    .build()
            )

            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Connectors")
                    .addText(settings.selectedMapConnectorTypes.joinToString(", ").take(100))
                    .setOnClickListener {
                        screenManager.push(AutoMapConnectorSelectionScreen(carContext, settingsManager))
                    }
                    .build()
            )
        }

        if (providers.contains(PoiProviderType.Overpass)) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("POI Types")
                    .addText(settings.selectedOverpassAmenityTypes.joinToString(", ").take(100))
                    .setOnClickListener {
                        screenManager.push(AutoMapOverpassAmenitySelectionScreen(carContext, settingsManager))
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle("Advanced Filters").setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
