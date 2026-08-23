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

    val queryParams: Map<String, List<String>> get() = parseQueryParams(url)
}

fun parseQueryParams(url: String): Map<String, List<String>> {
    val queryIndex = url.indexOf('?')
    if (queryIndex == -1) return emptyMap()
    val fragmentIndex = url.indexOf('#', queryIndex)
    val queryString = if (fragmentIndex != -1) {
        url.substring(queryIndex + 1, fragmentIndex)
    } else {
        url.substring(queryIndex + 1)
    }
    if (queryString.isBlank()) return emptyMap()

    val map = mutableMapOf<String, MutableList<String>>()
    queryString.split('&', ';').forEach { param ->
        if (param.isNotBlank()) {
            val parts = param.split('=', limit = 2)
            val key = decodeUrlComponent(parts[0])
            val value = if (parts.size > 1) decodeUrlComponent(parts[1]) else ""
            if (key.isNotEmpty()) {
                map.getOrPut(key) { mutableListOf() }.add(value)
            }
        }
    }
    return map
}

fun decodeUrlComponent(s: String): String {
    val result = StringBuilder()
    var i = 0
    val bytes = mutableListOf<Byte>()

    fun flushBytes() {
        if (bytes.isNotEmpty()) {
            val byteArray = bytes.toByteArray()
            result.append(byteArray.decodeToString())
            bytes.clear()
        }
    }

    while (i < s.length) {
        val c = s[i]
        when (c) {
            '+' -> {
                flushBytes()
                result.append(' ')
                i++
            }
            '%' -> {
                if (i + 2 < s.length) {
                    val hex = s.substring(i + 1, i + 3)
                    val b = hex.toIntOrNull(16)
                    if (b != null) {
                        bytes.add(b.toByte())
                        i += 3
                    } else {
                        flushBytes()
                        result.append('%')
                        i++
                    }
                } else {
                    flushBytes()
                    result.append('%')
                    i++
                }
            }
            else -> {
                flushBytes()
                result.append(c)
                i++
            }
        }
    }
    flushBytes()
    return result.toString()
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
