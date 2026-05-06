package fr.geoking.gaston.shared.diagnostics

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

@Serializable
data class DetailedError(
    val httpCode: Int?,
    val message: String,
    val timestamp: Long
)

private const val MAX_ERROR_LOG = 50

/**
 * Lightweight app diagnostics (API / auth / map errors). Replaces the former conversation + voice stack.
 */
@Stable
class DiagnosticStore {
    private val _errorLog = MutableStateFlow<List<DetailedError>>(emptyList())
    val errorLog: StateFlow<List<DetailedError>> = _errorLog.asStateFlow()

    fun recordError(httpCode: Int?, message: String) {
        val entry = DetailedError(httpCode, message, System.currentTimeMillis())
        _errorLog.update { (listOf(entry) + it).take(MAX_ERROR_LOG) }
    }

    /**
     * Replaces the current log with a previously persisted log.
     * Keeps newest-first ordering and enforces the maximum size.
     */
    fun loadErrors(errors: List<DetailedError>) {
        _errorLog.value = errors.sortedByDescending { it.timestamp }.take(MAX_ERROR_LOG)
    }

    fun clearErrors() {
        _errorLog.value = emptyList()
    }
}
