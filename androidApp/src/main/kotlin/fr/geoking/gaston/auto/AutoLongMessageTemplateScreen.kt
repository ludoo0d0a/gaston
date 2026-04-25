package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.LongMessageTemplate
import androidx.car.app.model.Template

class AutoLongMessageTemplateScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoLongMessageTemplateScreen") {
        val longText = (1..20).joinToString("\n") { "This is line $it of the long message." }

        LongMessageTemplate.Builder(longText)
            .setTitle("LongMessage Sample")
            .setHeaderAction(Action.BACK)
            .addAction(
                Action.Builder()
                    .setTitle("Accept")
                    .setOnClickListener { screenManager.pop() }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Decline")
                    .setOnClickListener { screenManager.pop() }
                    .build()
            )
            .build()
    }
}
