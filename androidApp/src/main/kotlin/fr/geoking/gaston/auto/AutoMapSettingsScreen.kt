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
                    append(carContext.getString(fr.geoking.gaston.R.string.auto_by_country))
                    if (!countryLine.isNullOrBlank()) {
                        append("\n").append(carContext.getString(fr.geoking.gaston.R.string.network_country_format, countryLine))
                    }
                }
            }
            PoiProviderSelectionMode.Manual ->
                if (settings.selectedPoiProviders.isEmpty()) carContext.getString(fr.geoking.gaston.R.string.none_label)
                else settings.selectedPoiProviders.joinToString(", ") { it.name }
        }

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(fr.geoking.gaston.R.string.data_source))
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
                .setTitle(carContext.getString(fr.geoking.gaston.R.string.map_mode))
                .addText(carContext.getString(fr.geoking.gaston.R.string.current_format, settings.carMapMode.name) + if (settings.carMapMode == CarMapMode.Custom) " " + carContext.getString(fr.geoking.gaston.R.string.map_mode_warning) else "")
                .setOnClickListener {
                    val nextMode = if (settings.carMapMode == CarMapMode.Native) CarMapMode.Custom else CarMapMode.Native
                    settingsManager.setCarMapMode(nextMode)
                    invalidate()
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(fr.geoking.gaston.R.string.show_traffic))
                .addText(carContext.getString(fr.geoking.gaston.R.string.google_traffic_layer))
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
                .setTitle(carContext.getString(fr.geoking.gaston.R.string.vehicle_range_title))
                .addText(carContext.getString(fr.geoking.gaston.R.string.vehicle_range_summary, settings.vehicleType.name, settings.evRangeKm))
                .setOnClickListener {
                    screenManager.push(AutoVehicleSettingsScreen(carContext, settingsManager))
                }
                .build()
        )


        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle(carContext.getString(fr.geoking.gaston.R.string.map_settings)).setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
