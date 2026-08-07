package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.di.MapDeps
import fr.geoking.gaston.ui.OVERPASS_AMENITY_OPTIONS

class AutoOtherDashboardScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager,
    private val getMapDeps: () -> MapDeps?
) : Screen(carContext) {

    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoOtherDashboardScreen") {
        val gridBuilder = ItemList.Builder()

        OVERPASS_AMENITY_OPTIONS.forEach { (id, resId) ->
            val label = carContext.getString(resId)
            val iconResId = getAmenityIcon(id)

            gridBuilder.addItem(
                GridItem.Builder()
                    .setTitle(label)
                    .setImage(carContext.carIcon(iconResId, AutoCarIcons.primary))
                    .setOnClickListener {
                        settingsManager.setOtherMode(id)
                        val mapDeps = getMapDeps()
                        if (mapDeps != null) {
                            screenManager.pop()
                            pushMapScreen(settingsManager, mapDeps, label)
                        }
                    }
                    .build()
            )
        }

        GridTemplate.Builder()
            .setSingleList(gridBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.search_mode_other))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }

    private fun getAmenityIcon(id: String): Int = when (id) {
        "toilets" -> R.drawable.ic_poi_toilet
        "drinking_water" -> R.drawable.ic_poi_water
        "camp_site" -> R.drawable.ic_poi_camping
        "caravan_site" -> R.drawable.ic_poi_caravan
        "picnic_site" -> R.drawable.ic_poi_picnic
        "truck_stop" -> R.drawable.ic_poi_truck
        "rest_area" -> R.drawable.ic_poi_rest_area
        "restaurant" -> R.drawable.ic_poi_restaurant
        "fast_food" -> R.drawable.ic_poi_fast_food
        "speed_camera" -> R.drawable.ic_poi_radar
        "parking" -> R.drawable.ic_poi_parking
        "viewpoint" -> R.drawable.ic_poi_viewpoint
        "post_box" -> R.drawable.ic_poi_post_box
        "water" -> R.drawable.ic_poi_water_body
        "cafe" -> R.drawable.ic_poi_cafe
        "supermarket" -> R.drawable.ic_poi_supermarket
        else -> R.drawable.ic_category
    }
}
