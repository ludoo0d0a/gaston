package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.RoutePreviewNavigationTemplate

class AutoRoutePreviewNavigationTemplateScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoRoutePreviewNavigationTemplateScreen") {
        val listBuilder = ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle("Fastest Route")
                    .addText("25 min")
                    .setOnClickListener { /* Select route */ }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Shortest Route")
                    .addText("30 min")
                    .setOnClickListener { /* Select route */ }
                    .build()
            )

        RoutePreviewNavigationTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle("Route Preview")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .setItemList(listBuilder.build())
            .setNavigateAction(
                Action.Builder()
                    .setTitle("Navigate")
                    .setOnClickListener { /* Start nav */ }
                    .build()
            )
            .build()
    }
}
