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
                    .setTitle(carContext.getString(R.string.template_primary_action))
                    .setOnClickListener { /* No-op */ }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.template_secondary))
                    .setOnClickListener { /* No-op */ }
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.template_pane_row_1))
                    .addText(carContext.getString(R.string.template_pane_info_1))
                    .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_map)).build())
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.template_pane_row_2))
                    .addText(carContext.getString(R.string.template_pane_info_2))
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.template_pane_row_3))
                    .addText(carContext.getString(R.string.template_pane_info_3))
                    .build()
            )

        PaneTemplate.Builder(paneBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.template_pane_sample))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }
}
