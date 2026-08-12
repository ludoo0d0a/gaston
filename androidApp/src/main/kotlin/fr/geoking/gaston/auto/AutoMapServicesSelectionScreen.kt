package fr.geoking.gaston.auto

import fr.geoking.gaston.R
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.ui.MAP_SERVICES_OPTIONS

class AutoMapServicesSelectionScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()

        MAP_SERVICES_OPTIONS.take(6).forEach { (id, label) ->
            val isSelected = settings.selectedMapServices.contains(id)
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(label)
                    .addText(
                        if (isSelected) carContext.getString(R.string.filter_enabled)
                        else carContext.getString(R.string.filter_disabled)
                    )
                    .setToggle(
                        Toggle.Builder { checked ->
                            val current = settingsManager.settings.value.selectedMapServices
                            val next = if (checked) current + id else current - id
                            settingsManager.setMapServices(next)
                            invalidate()
                        }.setChecked(isSelected).build()
                    )
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle(carContext.getString(R.string.screen_services)).setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
