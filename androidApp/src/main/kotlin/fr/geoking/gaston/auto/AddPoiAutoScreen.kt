package fr.geoking.gaston.auto

import fr.geoking.gaston.R
import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import fr.geoking.gaston.community.CommunityPoiRepository
import fr.geoking.gaston.community.communityPoiId
import fr.geoking.gaston.poi.Poi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Android Auto screen to add a POI at current location (gas or IRVE).
 */
class AddPoiAutoScreen(
    carContext: CarContext,
    private val communityRepo: CommunityPoiRepository,
    private val lat: Double,
    private val lng: Double,
    private val onDone: () -> Unit
) : androidx.car.app.Screen(carContext) {

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onGetTemplate(): Template {
        val coordStr = "%.4f, %.4f".format(lat, lng)
        return MessageTemplate.Builder("Add a station at current location?\n$coordStr")
            .setHeader(Header.Builder().setTitle(carContext.getString(R.string.screen_add_poi)).setStartHeaderAction(Action.BACK).build())
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.gas_station))
                    .setOnClickListener {
                        addPoi(isElectric = false)
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.poi_irve_charging))
                    .setOnClickListener {
                        addPoi(isElectric = true)
                    }
                    .build()
            )
            .build()
    }

    private fun addPoi(isElectric: Boolean) {
        scope.launch {
            val name = if (isElectric) "IRVE" else "Gas station"
            val poi = Poi(
                id = communityPoiId(),
                name = name,
                address = "%.4f, %.4f".format(lat, lng),
                latitude = lat,
                longitude = lng,
                isElectric = isElectric
            )
            communityRepo.addCommunityPoi(poi, null)
            onDone()
            screenManager.pop()
        }
    }
}
