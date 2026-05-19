package fr.geoking.gaston.auto

import fr.geoking.gaston.R
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template

class AutoListTemplateScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        // Standard Android Auto list limit is 6 items for many templates.
        for (i in 1..6) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("List Item $i")
                    .addText("Description for item $i")
                    .setOnClickListener { /* No-op */ }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.template_list_sample))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .setSingleList(listBuilder.build())
            .build()
    }
}
