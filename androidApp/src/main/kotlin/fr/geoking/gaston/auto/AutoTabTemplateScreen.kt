package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Tab
import androidx.car.app.model.TabContents
import androidx.car.app.model.TabTemplate
import androidx.car.app.model.Template

class AutoTabTemplateScreen(carContext: CarContext) : Screen(carContext) {
    private var activeTabId = "fuel"

    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoTabTemplateScreen") {
        val tabBuilder = TabTemplate.Builder(object : TabTemplate.TabCallback {
            override fun onTabSelected(tabId: String) {
                activeTabId = tabId
                invalidate()
            }
        })
            .setHeaderAction(Action.BACK)
            .addTab(
                Tab.Builder()
                    .setTitle("Fuel")
                    .setContentId("fuel")
                    .build()
            )
            .addTab(
                Tab.Builder()
                    .setTitle("Electric")
                    .setContentId("ev")
                    .build()
            )
            .setActiveTabContentId(activeTabId)

        val listBuilder = ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle("Selected Tab: $activeTabId")
                    .build()
            )

        // Nested ListTemplate in TabContents must not have a header or title.
        val listTemplate = ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .build()

        tabBuilder.setTabContents(
            TabContents.Builder(listTemplate).build()
        ).build()
    }
}
