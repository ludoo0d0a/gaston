package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import fr.geoking.gaston.R

class AutoGridTemplateScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoGridTemplateScreen") {
        val gridBuilder = ItemList.Builder()
        for (i in 1..6) {
            gridBuilder.addItem(
                GridItem.Builder()
                    .setTitle("Item $i")
                    .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_map)).build())
                    .setOnClickListener { /* No-op */ }
                    .build()
            )
        }

        GridTemplate.Builder()
            .setSingleList(gridBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.template_grid_sample))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }
}
