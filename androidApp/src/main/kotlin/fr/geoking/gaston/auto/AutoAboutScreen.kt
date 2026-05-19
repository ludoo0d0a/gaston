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
            appendLine("Gaston")
            appendLine()
            appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Build date: ${BuildConfig.BUILD_DATE}")
            appendLine()
            appendLine("Used APIs & services:")
            UsedApisList.forEach { api ->
                val host = api.url.removePrefix("https://").removePrefix("http://").substringBefore('/')
                appendLine("- ${api.name} — $host")
            }
        }.trim().take(5000)

        LongMessageTemplate.Builder(body)
            .setTitle(carContext.getString(R.string.screen_about))
            .setHeaderAction(Action.BACK)
            .build()
    }
}
