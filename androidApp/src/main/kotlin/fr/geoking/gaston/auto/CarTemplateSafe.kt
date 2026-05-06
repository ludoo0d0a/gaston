package fr.geoking.gaston.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import fr.geoking.gaston.shared.diagnostics.DiagnosticStore
import org.koin.core.context.GlobalContext

/**
 * Builds a car template and, on failure, returns a [MessageTemplate] so the head unit shows a clear
 * error instead of crashing the session (common with strict template validators in the library).
 */
internal fun safeCarTemplate(
    carContext: CarContext,
    logTag: String,
    templateName: String? = null,
    block: () -> Template
): Template = try {
    block()
} catch (e: Exception) {
    Log.e(logTag, "onGetTemplate failed", e)

    fun Exception.rootCause(): Throwable {
        var cur: Throwable = this
        while (cur.cause != null && cur.cause !== cur) cur = cur.cause!!
        return cur
    }

    val root = e.rootCause()
    val screenLine = "Screen: $logTag"
    val templateLine = templateName?.takeIf { it.isNotBlank() }?.let { "Template: $it" } ?: "Template: (unknown)"
    val errorLine = "Error: ${e::class.java.simpleName}${e.message?.trim()?.takeIf { it.isNotEmpty() }?.let { ": $it" } ?: ""}"
    val rootLine =
        if (root !== e) {
            "Cause: ${root::class.java.simpleName}${root.message?.trim()?.takeIf { it.isNotEmpty() }?.let { ": $it" } ?: ""}"
        } else {
            null
        }

    val body = listOfNotNull(
        "This screen couldn't be built (host rejected template or template validation failed).",
        screenLine,
        templateLine,
        errorLine,
        rootLine
    ).joinToString(separator = "\n").take(500)

    // Persist for later retrieval from Settings (phone).
    runCatching {
        GlobalContext.get().get<DiagnosticStore>().recordError(
            httpCode = null,
            message = buildString {
                append("AndroidAuto template error\n")
                append(screenLine).append('\n')
                append(templateLine).append('\n')
                append(errorLine)
                if (rootLine != null) append('\n').append(rootLine)
            }
        )
    }

    MessageTemplate.Builder(body)
        .setHeader(
            Header.Builder()
                .setTitle("Template error")
                .setStartHeaderAction(Action.BACK)
                .build()
        )
        .build()
}
