package fr.geoking.gaston.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template

/**
 * Builds a car template and, on failure, returns a [MessageTemplate] so the head unit shows a clear
 * error instead of crashing the session (common with strict template validators in the library).
 */
internal fun safeCarTemplate(
    carContext: CarContext,
    logTag: String,
    block: () -> Template
): Template = try {
    block()
} catch (e: Exception) {
    Log.e(logTag, "onGetTemplate failed", e)
    val detail = e.message?.trim()?.take(280)?.let { "\n\n$it" } ?: ""
    val body = ("This screen could not be built for this Android Auto version or head unit.$detail").take(500)
    MessageTemplate.Builder(body)
        .setHeader(
            Header.Builder()
                .setTitle("Template error")
                .setStartHeaderAction(Action.BACK)
                .build()
        )
        .build()
}
