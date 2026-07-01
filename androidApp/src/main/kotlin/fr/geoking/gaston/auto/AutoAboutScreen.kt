package fr.geoking.gaston.auto

import fr.geoking.gaston.R
import fr.geoking.gaston.UsedApisList
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.LongMessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import fr.geoking.gaston.BuildConfig

class AutoAboutScreen(
    carContext: CarContext
) : Screen(carContext) {

    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoAboutScreen") {
        val listBuilder = ItemList.Builder()

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.app_name))
                .addText(
                    carContext.getString(
                        R.string.about_version_line,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE
                    )
                )
                .addText(carContext.getString(R.string.about_build_date_line, BuildConfig.BUILD_DATE))
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.about_view_disclaimer))
                .setImage(carContext.actionHistoryIcon())
                .setOnClickListener {
                    screenManager.push(AutoDisclaimerScreen(carContext))
                }
                .setBrowsable(true)
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.about_used_apis))
                .setImage(carContext.dashboardOtherIcon())
                .setOnClickListener {
                    screenManager.push(AutoSourcesScreen(carContext))
                }
                .setBrowsable(true)
                .build()
        )

        ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.screen_about))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }
}

class AutoDisclaimerScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoDisclaimerScreen") {
        LongMessageTemplate.Builder(carContext.getString(R.string.disclaimer_content))
            .setTitle(carContext.getString(R.string.about_view_disclaimer))
            .setHeaderAction(Action.BACK)
            .build()
    }
}

class AutoSourcesScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoSourcesScreen") {
        val body = buildString {
            UsedApisList.forEach { api ->
                val host = api.url.removePrefix("https://").removePrefix("http://").substringBefore('/')
                appendLine(carContext.getString(R.string.about_api_line, api.name, host))
            }
        }.trim().take(5000)

        LongMessageTemplate.Builder(body)
            .setTitle(carContext.getString(R.string.about_used_apis))
            .setHeaderAction(Action.BACK)
            .build()
    }
}
