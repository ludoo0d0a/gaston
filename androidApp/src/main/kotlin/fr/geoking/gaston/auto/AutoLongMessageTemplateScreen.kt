package fr.geoking.gaston.auto

import fr.geoking.gaston.R
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.LongMessageTemplate
import androidx.car.app.model.Template

class AutoLongMessageTemplateScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoLongMessageTemplateScreen") {
        val longText = (1..20).joinToString("\n") { "This is line $it of the long message." }

        LongMessageTemplate.Builder(longText)
            .setTitle(carContext.getString(R.string.template_long_message_sample))
            .setHeaderAction(Action.BACK)
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.action_accept))
                    .setOnClickListener { screenManager.pop() }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.action_decline))
                    .setOnClickListener { screenManager.pop() }
                    .build()
            )
            .build()
    }
}
