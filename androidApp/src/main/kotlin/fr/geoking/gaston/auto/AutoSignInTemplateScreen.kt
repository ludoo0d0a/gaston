package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Template
import androidx.car.app.model.signin.SignInTemplate
import androidx.car.app.model.signin.PinSignInMethod

class AutoSignInTemplateScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoSignInTemplateScreen") {
        val signInMethod = PinSignInMethod("123456")

        SignInTemplate.Builder(signInMethod)
            .setTitle("SignInTemplate Sample")
            .setHeaderAction(Action.BACK)
            .setInstructions("Please enter this PIN on your phone.")
            .addAction(
                Action.Builder()
                    .setTitle("Done")
                    .setOnClickListener { screenManager.pop() }
                    .build()
            )
            .build()
    }
}
