package fr.geoking.gaston.auto

import fr.geoking.gaston.R
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.gaston.SettingsManager

class AutoGeneralFiltersScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.screen_energy_types))
                .addText(settings.selectedMapEnergyTypes.joinToString(", ").take(100))
                .setOnClickListener {
                    screenManager.push(AutoEnergyMenuScreen(carContext, settingsManager))
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.screen_brands))
                .addText(
                    if (settings.mapBrands.isEmpty()) carContext.getString(R.string.action_all)
                    else settings.mapBrands.joinToString(", ").take(100)
                )
                .setOnClickListener {
                    screenManager.push(AutoMapBrandSelectionScreen(carContext, settingsManager))
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.screen_enseigne))
                .addText(settings.mapEnseigneType)
                .setOnClickListener {
                    screenManager.push(AutoMapEnseigneSelectionScreen(carContext, settingsManager))
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.screen_services))
                .addText(settings.selectedMapServices.joinToString(", ").take(100))
                .setOnClickListener {
                    screenManager.push(AutoMapServicesSelectionScreen(carContext, settingsManager))
                }
                .build()
        )

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle(carContext.getString(R.string.screen_general_filters)).setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
