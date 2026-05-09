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
 *
 * Known Android Auto template constraints enforced here at build-time:
 * - Row.IMAGE_TYPE_LARGE cannot coexist with Metadata containing a Place.
 * - MapWithContentTemplate ActionStrip: 1–4 actions, each with an icon (no title-only).
 * - ListTemplate inside MapWithContentTemplate: max rows from ConstraintManager (default 6).
 * - Navigation rows must call setBrowsable(true).
 * - MapWithContentTemplate requires MAP_TEMPLATES + ACCESS_SURFACE permissions and minCarApiLevel 7.
 */
internal fun safeCarTemplate(
    carContext: CarContext,
    logTag: String,
    templateName: String? = null,
    block: () -> Template
): Template = try {
    block()
} catch (e: Exception) {
    // Always log the full stack trace so it is visible in logcat/DHU debugging.
    Log.e(logTag, "onGetTemplate FAILED — screen=$logTag template=${templateName ?: "?"}", e)

    fun Throwable.rootCause(): Throwable {
        var cur: Throwable = this
        while (cur.cause != null && cur.cause !== cur) cur = cur.cause!!
        return cur
    }

    val root = e.rootCause()

    val screenLine    = "Screen: $logTag"
    val templateLine  = "Template: ${templateName?.takeIf { it.isNotBlank() } ?: "(unknown)"}"
    val errorLine     = "Error: ${e::class.java.simpleName}: ${e.message?.trim() ?: "(no message)"}"
    val rootLine      = if (root !== e as Throwable)
        "Cause: ${root::class.java.simpleName}: ${root.message?.trim() ?: "(no message)"}"
    else null

    // Top app-package stack frames (skip Android/Androidx framework noise).
    val appPkg = carContext.packageName.substringBeforeLast('.')
    val frames = e.stackTrace
        .filter { it.className.startsWith(appPkg) || it.className.startsWith("fr.geoking") }
        .take(5)
        .joinToString("\n") { "  at ${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
    val stackSection = if (frames.isNotBlank()) "Stack (app frames):\n$frames" else null

    val body = listOfNotNull(
        "Template build failed — host will reject this screen.",
        screenLine,
        templateLine,
        errorLine,
        rootLine,
        stackSection
    ).joinToString(separator = "\n")

    // Persist full detail for later retrieval from Settings → Diagnostics on phone.
    runCatching {
        GlobalContext.get().get<DiagnosticStore>().recordError(
            httpCode = null,
            message = buildString {
                appendLine("AndroidAuto template error")
                appendLine(screenLine)
                appendLine(templateLine)
                appendLine(errorLine)
                if (rootLine != null) appendLine(rootLine)
                appendLine("Full stack:")
                appendLine(e.stackTraceToString().take(2000))
            }
        )
    }

    // MessageTemplate body is capped at ~500 chars on most hosts; keep on-screen text concise.
    MessageTemplate.Builder(body.take(500))
        .setHeader(
            Header.Builder()
                .setTitle("[$logTag] Template error")
                .setStartHeaderAction(Action.BACK)
                .build()
        )
        .build()
}
