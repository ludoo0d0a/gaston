package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import fr.geoking.gaston.R

class AutoPaneTemplateScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoPaneTemplateScreen") {
        val paneBuilder = Pane.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Primary Action")
                    .setOnClickListener { /* No-op */ }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Secondary")
                    .setOnClickListener { /* No-op */ }
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("Pane Row 1")
                    .addText("Additional information about this item.")
                    .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_map)).build())
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("Pane Row 2")
                    .addText("More details here.")
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("Pane Row 3")
                    .addText("Third line of information.")
                    .build()
            )

        PaneTemplate.Builder(paneBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle("PaneTemplate Sample")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }
}
