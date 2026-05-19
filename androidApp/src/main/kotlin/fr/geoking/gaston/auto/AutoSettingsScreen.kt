package fr.geoking.gaston.auto

import fr.geoking.gaston.R
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
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
                .setTitle(carContext.getString(R.string.screen_app_theme))
                .addText(
                    carContext.getString(
                        R.string.settings_current_format,
                        settingsManager.settings.value.uiThemeMode.name
                    )
                )
                .setOnClickListener {
                    screenManager.push(AutoThemeSelectionScreen(carContext, settingsManager))
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.screen_download_toll_data))
                .addText(carContext.getString(R.string.settings_toll_french_highway))
                .setOnClickListener {
                    screenManager.push(AutoTollDataScreen(carContext, settingsManager))
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.screen_about))
                .addText(carContext.getString(R.string.settings_version_sources))
                .setOnClickListener {
                    screenManager.push(AutoAboutScreen(carContext))
                }
                .build()
        )

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle(carContext.getString(R.string.cd_settings)).setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
