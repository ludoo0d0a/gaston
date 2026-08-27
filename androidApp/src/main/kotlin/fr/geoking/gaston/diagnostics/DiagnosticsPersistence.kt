package fr.geoking.gaston.diagnostics

import android.content.Context
import android.util.Log
import fr.geoking.gaston.shared.diagnostics.DetailedError
import fr.geoking.gaston.shared.diagnostics.DiagnosticStore
import fr.geoking.gaston.shared.diagnostics.ErrorSeverity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Persists [DiagnosticStore.errorLog] so it can be retrieved later from Settings and copied.
 * This captures errors from both phone UI and Android Auto (same process).
 */
@OptIn(FlowPreview::class)
class DiagnosticsPersistence(
    context: Context,
    private val diagnostics: DiagnosticStore,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        restore()
        scope.launch {
            diagnostics.errorLog
                // Avoid excessive writes if multiple errors happen quickly
                .debounce(350)
                .collectLatest { save(it) }
        }
    }

    private fun restore() {
        val raw = prefs.getString(KEY_ERROR_LOG, null) ?: return
        runCatching {
            json.decodeFromString(ListSerializer(DetailedError.serializer()), raw)
        }.onSuccess { list ->
            diagnostics.loadErrors(list)
        }.onFailure { e ->
            Log.e(TAG, "Failed to restore diagnostics", e)
        }
    }

    private fun save(list: List<DetailedError>) {
        runCatching {
            json.encodeToString(ListSerializer(DetailedError.serializer()), list)
        }.onSuccess { raw ->
            prefs.edit().putString(KEY_ERROR_LOG, raw).apply()
        }.onFailure { e ->
            Log.e(TAG, "Failed to persist diagnostics", e)
        }
    }

    companion object {
        private const val TAG = "DiagnosticsPersistence"
        private const val PREFS_NAME = "gaston_diagnostics"
        private const val KEY_ERROR_LOG = "error_log_v1"

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun persistCrash(context: Context, throwable: Throwable) {
            runCatching {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val raw = prefs.getString(KEY_ERROR_LOG, null)
                val existingList = if (raw != null) {
                    runCatching { json.decodeFromString(ListSerializer(DetailedError.serializer()), raw) }.getOrDefault(emptyList())
                } else emptyList()

                val stackTrace = Log.getStackTraceString(throwable)
                val message = throwable.localizedMessage ?: throwable.message ?: throwable.javaClass.name
                val crashEntry = DetailedError(
                    httpCode = null,
                    message = message,
                    timestamp = System.currentTimeMillis(),
                    level = ErrorSeverity.CRASH,
                    stackTrace = stackTrace
                )
                val updatedList = (listOf(crashEntry) + existingList).take(50)
                val encoded = json.encodeToString(ListSerializer(DetailedError.serializer()), updatedList)
                prefs.edit().putString(KEY_ERROR_LOG, encoded).commit()
            }.onFailure { e ->
                Log.e(TAG, "Failed to persist crash", e)
            }
        }
    }
}

