package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Header
import androidx.car.app.navigation.model.MapController

/** Shared MapWithContent chrome: compass / recenter in the list header, zoom on the map strip. */
object AaMapChromeTemplate {

    fun contentHeader(
        carContext: CarContext,
        title: String,
        controls: AaCanvasMapControls,
    ): Header.Builder = Header.Builder()
        .setTitle(title)
        .setStartHeaderAction(Action.BACK)
        .addEndHeaderAction(
            Action.Builder()
                .setIcon(carContext.actionCompassIcon())
                .setOnClickListener { controls.toggleMapOrientation() }
                .build()
        )
        .addEndHeaderAction(
            Action.Builder()
                .setIcon(carContext.actionRecenterIcon())
                .setOnClickListener { controls.recenterMap() }
                .build()
        )

    fun zoomMapController(
        carContext: CarContext,
        controls: AaCanvasMapControls,
    ): MapController = MapController.Builder()
        .setMapActionStrip(
            ActionStrip.Builder()
                .addAction(
                    Action.Builder()
                        .setIcon(carContext.actionZoomInIcon())
                        .setOnClickListener { controls.bumpZoom(1) }
                        .build()
                )
                .addAction(
                    Action.Builder()
                        .setIcon(carContext.actionZoomOutIcon())
                        .setOnClickListener { controls.bumpZoom(-1) }
                        .build()
                )
                .build()
        )
        .build()
}
