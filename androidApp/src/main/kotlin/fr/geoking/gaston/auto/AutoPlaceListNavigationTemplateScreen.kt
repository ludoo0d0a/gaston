package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarLocation
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.Metadata
import androidx.car.app.model.Place
import androidx.car.app.navigation.model.PlaceListNavigationTemplate
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.car.app.model.Template

class AutoPlaceListNavigationTemplateScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoPlaceListNavigationTemplateScreen") {
        val listBuilder = ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle("Eiffel Tower")
                    .addText("Champ de Mars, 5 Avenue Anatole France, 75007 Paris")
                    .setBrowsable(true)
                    .setMetadata(
                        Metadata.Builder()
                            .setPlace(
                                Place.Builder(CarLocation.create(48.8584, 2.2945))
                                    .setMarker(PlaceMarker.Builder().build())
                                    .build()
                            )
                            .build()
                    )
                    .setOnClickListener { /* No-op */ }
                    .build()
            )

        PlaceListNavigationTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle("PlaceListNavigationTemplate")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .setItemList(listBuilder.build())
            .setActionStrip(
                androidx.car.app.model.ActionStrip.Builder()
                    .addAction(Action.Builder().setTitle("Exit").setOnClickListener { screenManager.pop() }.build())
                    .build()
            )
            .build()
    }
}
