package fr.geoking.gaston.auto

import fr.geoking.gaston.R
import fr.geoking.gaston.UsedApisList
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.LongMessageTemplate
import androidx.car.app.model.Template
import fr.geoking.gaston.BuildConfig

class AutoAboutScreen(
    carContext: CarContext
) : Screen(carContext) {

    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoAboutScreen") {
        val body = buildString {
            appendLine(carContext.getString(R.string.app_name))
            appendLine()
            appendLine(
                carContext.getString(
                    R.string.about_version_line,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE
                )
            )
            appendLine(carContext.getString(R.string.about_build_date_line, BuildConfig.BUILD_DATE))
            appendLine()
            appendLine(carContext.getString(R.string.about_used_apis_header))
            UsedApisList.forEach { api ->
                val host = api.url.removePrefix("https://").removePrefix("http://").substringBefore('/')
                appendLine(carContext.getString(R.string.about_api_line, api.name, host))
            }
        }.trim().take(5000)

        LongMessageTemplate.Builder(body)
            .setTitle(carContext.getString(R.string.screen_about))
            .setHeaderAction(Action.BACK)
            .build()
    }
}
