package fr.geoking.gaston.shared.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class NetworkLog(
    val id: String,
    val url: String,
    val host: String,
    val method: String,
    val requestHeaders: Map<String, List<String>>,
    val requestBody: String?,
    val responseHeaders: Map<String, List<String>>?,
    val responseBody: String?,
    val statusCode: Int?,
    val durationMs: Long,
    val timestamp: Long
) {
    // Helper to allow reading properties from other modules without smart cast issues on nullables
    val safeRequestBody: String get() = requestBody ?: ""
    val safeResponseBody: String get() = responseBody ?: ""
}

object DebugLogStore {
    private val _logs = MutableStateFlow<List<NetworkLog>>(emptyList())
    val logs: StateFlow<List<NetworkLog>> = _logs.asStateFlow()

    private const val MAX_LOGS = 50

    fun addLog(log: NetworkLog) {
        _logs.update { current ->
            val next = current.toMutableList()
            next.add(0, log)
            if (next.size > MAX_LOGS) {
                next.take(MAX_LOGS)
            } else {
                next
            }
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun clearAll() {
        clearLogs()
        ProviderTraceStore.clear()
    }
}
