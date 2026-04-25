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

class AutoMessageTemplateScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoMessageTemplateScreen") {
        MessageTemplate.Builder("This is a MessageTemplate sample. It can show a message, an icon, and up to two actions.")
            .setHeader(
                Header.Builder()
                    .setTitle("MessageTemplate")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_map)).build())
            .addAction(
                Action.Builder()
                    .setTitle("OK")
                    .setOnClickListener { screenManager.pop() }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Cancel")
                    .setOnClickListener { screenManager.pop() }
                    .build()
            )
            .build()
    }
}
