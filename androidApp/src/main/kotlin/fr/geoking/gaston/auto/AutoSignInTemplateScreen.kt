package fr.geoking.gaston.auto

import fr.geoking.gaston.R
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
            .setTitle(carContext.getString(R.string.template_sign_in_sample))
            .setHeaderAction(Action.BACK)
            .setInstructions("Please enter this PIN on your phone.")
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.action_done))
                    .setOnClickListener { screenManager.pop() }
                    .build()
            )
            .build()
    }
}
