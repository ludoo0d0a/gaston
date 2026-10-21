package fr.geoking.gaston.auto

import fr.geoking.gaston.R
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.ui.OVERPASS_AMENITY_OPTIONS

class AutoMapOverpassAmenitySelectionScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()

        // Traffic / radar / weather are phone shortcuts; AA only keeps the radar alert toggle.
        OVERPASS_AMENITY_OPTIONS
            .filter { (id, _) -> id != "weather" && id != "speed_camera" }
            .forEach { (id, resId) ->
            val isSelected = settings.selectedOverpassAmenityTypes.contains(id)
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(carContext.getString(resId))
                    .addText(
                        if (isSelected) carContext.getString(R.string.filter_enabled)
                        else carContext.getString(R.string.filter_disabled)
                    )
                    .setToggle(
                        Toggle.Builder { checked ->
                            val current = settingsManager.settings.value.selectedOverpassAmenityTypes
                            val next = if (checked) current + id else current - id
                            settingsManager.setOverpassAmenityTypes(next)
                            invalidate()
                        }.setChecked(isSelected).build()
                    )
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle(carContext.getString(R.string.screen_poi_types)).setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
