package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.gaston.CarMapMode
import fr.geoking.gaston.PoiProviderSelectionMode
import fr.geoking.gaston.SettingsManager

class AutoMapSettingsScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Data Source")
                .addText(
                    when (settings.poiProviderSelectionMode) {
                        PoiProviderSelectionMode.Auto -> "Auto (by country)"
                        PoiProviderSelectionMode.Manual ->
                            if (settings.selectedPoiProviders.isEmpty()) "None"
                            else settings.selectedPoiProviders.joinToString(", ") { it.name }
                    }
                )
                .setOnClickListener {
                    val next = if (settings.poiProviderSelectionMode == PoiProviderSelectionMode.Manual) {
                        PoiProviderSelectionMode.Auto
                    } else {
                        PoiProviderSelectionMode.Manual
                    }
                    settingsManager.setPoiProviderSelectionMode(next)
                    invalidate()
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Map Mode")
                .addText("Current: ${settings.carMapMode.name}")
                .setOnClickListener {
                    val nextMode = if (settings.carMapMode == CarMapMode.Native) CarMapMode.Custom else CarMapMode.Native
                    settingsManager.setCarMapMode(nextMode)
                    invalidate()
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Show Traffic")
                .addText("Google traffic layer")
                .setToggle(
                    Toggle.Builder { checked ->
                        settingsManager.setMapTrafficEnabled(checked)
                        invalidate()
                    }.setChecked(settings.mapTrafficEnabled).build()
                )
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Vehicle & Range")
                .addText("${settings.vehicleType.name}, ${settings.evRangeKm} km")
                .setOnClickListener {
                    screenManager.push(AutoVehicleSettingsScreen(carContext, settingsManager))
                }
                .build()
        )


        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle("Map Settings").setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
