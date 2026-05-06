package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarLocation
import androidx.car.app.model.ItemList
import androidx.car.app.model.Metadata
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.car.app.model.Template

class AutoPlaceListMapTemplateScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoPlaceListMapTemplateScreen") {
        val listBuilder = ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle("Eiffel Tower")
                    .addText("Champ de Mars, 5 Avenue Anatole France, 75007 Paris")
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
            .addItem(
                Row.Builder()
                    .setTitle("Louvre Museum")
                    .addText("Rue de Rivoli, 75001 Paris")
                    .setMetadata(
                        Metadata.Builder()
                            .setPlace(
                                Place.Builder(CarLocation.create(48.8606, 2.3376))
                                    .setMarker(PlaceMarker.Builder().build())
                                    .build()
                            )
                            .build()
                    )
                    .setOnClickListener { /* No-op */ }
                    .build()
            )

        val anchor = Place.Builder(CarLocation.create(48.8584, 2.2945))
            .setMarker(PlaceMarker.Builder().build())
            .build()

        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Home")
                    .setOnClickListener { screenManager.popToRoot() }
                    .build()
            )
            .build()

        PlaceListMapTemplate.Builder()
            .setTitle("PlaceListMapTemplate")
            .setHeaderAction(Action.BACK)
            .setActionStrip(actionStrip)
            .setAnchor(anchor)
            .setItemList(listBuilder.build())
            .build()
    }
}
