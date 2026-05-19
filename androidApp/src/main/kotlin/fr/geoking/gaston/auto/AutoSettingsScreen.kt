package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager

/** Android Auto: toll data and car-safe options. */
class AutoSettingsScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.app_theme))
                .addText(carContext.getString(R.string.current_theme, settingsManager.settings.value.uiThemeMode.name))
                .setOnClickListener {
                    screenManager.push(AutoThemeSelectionScreen(carContext, settingsManager))
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.download_toll_data))
                .addText(carContext.getString(R.string.highway_toll_desc))
                .setOnClickListener {
                    screenManager.push(AutoTollDataScreen(carContext, settingsManager))
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.about_title))
                .addText(carContext.getString(R.string.version_data_sources))
                .setOnClickListener {
                    screenManager.push(AutoAboutScreen(carContext))
                }
                .build()
        )

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle(carContext.getString(R.string.settings_title)).setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
