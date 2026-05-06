package fr.geoking.gaston.diagnostics

import android.content.Context
import android.util.Log
import fr.geoking.gaston.shared.diagnostics.DetailedError
import fr.geoking.gaston.shared.diagnostics.DiagnosticStore
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

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

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

    private companion object {
        private const val TAG = "DiagnosticsPersistence"
        private const val PREFS_NAME = "gaston_diagnostics"
        private const val KEY_ERROR_LOG = "error_log_v1"
    }
}

