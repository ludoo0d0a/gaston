package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.gaston.CarMapMode
import fr.geoking.gaston.PoiProviderSelectionMode
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.shared.network.NetworkService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AutoMapSettingsScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext), KoinComponent {

    private val networkService: NetworkService by inject()

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()

        val dataSourceText = when (settings.poiProviderSelectionMode) {
            PoiProviderSelectionMode.Auto -> {
                val net = networkService.status.value
                val countryLine = net.countryName ?: net.countryCode
                buildString {
                    append("Auto (by country)")
                    if (!countryLine.isNullOrBlank()) {
                        append("\nNetwork country: ").append(countryLine)
                    }
                }
            }
            PoiProviderSelectionMode.Manual ->
                if (settings.selectedPoiProviders.isEmpty()) "None"
                else settings.selectedPoiProviders.joinToString(", ") { it.name }
        }

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Data Source")
                .addText(dataSourceText)
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
                .addText(
                    "Current: ${settings.carMapMode.name}" +
                        if (settings.carMapMode == CarMapMode.Custom) {
                            " — north/heading toggle on map header (may not be POI-category compliant)"
                        } else {
                            " — map orientation is host-controlled (north-up)"
                        }
                )
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
