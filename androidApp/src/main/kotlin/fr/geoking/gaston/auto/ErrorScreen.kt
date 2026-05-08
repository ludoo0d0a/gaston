package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import fr.geoking.gaston.R

/**
 * Fallback screen shown when the Android Auto root screen fails to load (e.g. Koin/DI init error).
 * Displays the error message for debugging on real devices.
 */
class ErrorScreen(
    carContext: CarContext,
    private val errorMessage: String,
    private val errorDetail: String? = null,
    private val templateType: String? = null
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val detail = errorDetail?.take(200) ?: ""
        val templateLine = templateType?.takeIf { it.isNotBlank() }?.let { "Template: $it" } ?: ""
        val fullText = listOfNotNull(
            errorMessage.takeIf { it.isNotBlank() },
            templateLine.takeIf { it.isNotBlank() },
            detail.takeIf { it.isNotBlank() }
        ).joinToString(separator = "\n")

        return MessageTemplate.Builder(fullText.take(500))
            .setIcon(
                CarIcon.Builder(
                    IconCompat.createWithResource(carContext, R.drawable.auto_theme_idle)
                ).build()
            )
            .setHeader(Header.Builder().setTitle("gaston Error").setStartHeaderAction(Action.APP_ICON).build())
            .build()
    }
}
