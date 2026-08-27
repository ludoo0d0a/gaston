package fr.geoking.gaston.shared.diagnostics

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

@Serializable
enum class ErrorSeverity {
    CRASH,
    ERROR,
    WARNING
}

@Serializable
data class DetailedError(
    val httpCode: Int? = null,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val level: ErrorSeverity = ErrorSeverity.ERROR,
    val stackTrace: String? = null
)

private const val MAX_ERROR_LOG = 50

/**
 * Lightweight app diagnostics (API / auth / map errors). Replaces the former conversation + voice stack.
 */
@Stable
class DiagnosticStore {
    private val _errorLog = MutableStateFlow<List<DetailedError>>(emptyList())
    val errorLog: StateFlow<List<DetailedError>> = _errorLog.asStateFlow()

    fun recordError(
        httpCode: Int?,
        message: String,
        level: ErrorSeverity = ErrorSeverity.ERROR,
        stackTrace: String? = null
    ) {
        val entry = DetailedError(
            httpCode = httpCode,
            message = message,
            timestamp = System.currentTimeMillis(),
            level = level,
            stackTrace = stackTrace
        )
        _errorLog.update { (listOf(entry) + it).take(MAX_ERROR_LOG) }
    }

    fun recordCrash(message: String, stackTrace: String?) {
        recordError(
            httpCode = null,
            message = message,
            level = ErrorSeverity.CRASH,
            stackTrace = stackTrace
        )
    }

    fun recordWarning(message: String) {
        recordError(
            httpCode = null,
            message = message,
            level = ErrorSeverity.WARNING,
            stackTrace = null
        )
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
