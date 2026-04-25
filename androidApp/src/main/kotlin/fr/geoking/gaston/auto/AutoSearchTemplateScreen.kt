package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template

class AutoSearchTemplateScreen(carContext: CarContext) : Screen(carContext) {
    private var searchText = ""

    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoSearchTemplateScreen") {
        val listBuilder = ItemList.Builder()
        if (searchText.isNotBlank()) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Result for '$searchText'")
                    .setOnClickListener { /* No-op */ }
                    .build()
            )
        } else {
            listBuilder.setNoItemsMessage("Type something to search")
        }

        SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(text: String) {
                searchText = text
                invalidate()
            }

            override fun onSearchSubmitted(text: String) {
                searchText = text
                invalidate()
            }
        })
            .setHeaderAction(Action.BACK)
            .setSearchHint("Search something...")
            .setItemList(listBuilder.build())
            .build()
    }
}
