package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import fr.geoking.gaston.R

/**
 * Terminal confirmation after Save / Ignore from the park-candidate HUN.
 */
class AutoParkCandidateResultScreen(
    carContext: CarContext,
    private val message: String,
) : Screen(carContext) {

    override fun onGetTemplate(): Template = safeCarTemplate(
        carContext = carContext,
        logTag = "AutoParkCandidateResult",
        templateName = "MessageTemplate",
    ) {
        MessageTemplate.Builder(message.take(500))
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.parked_car_title))
                    .setStartHeaderAction(Action.APP_ICON)
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.action_ok))
                    .setOnClickListener { screenManager.popToRoot() }
                    .build()
            )
            .build()
    }
}
