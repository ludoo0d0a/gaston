package fr.geoking.gaston.shared.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class HostDataConsumption(
    val host: String,
    val providerName: String? = null,
    val bytesSent: Long = 0L,
    val bytesReceived: Long = 0L,
    val requestCount: Int = 0,
) {
    val totalBytes: Long get() = bytesSent + bytesReceived
}

fun resolveProviderName(host: String): String? {
    val h = host.lowercase()
    return when {
        h.contains("e-control.at") -> "Austria E-Control"
        h.contains("openchargemap") -> "OpenChargeMap"
        h.contains("data.economie.gouv.fr") || h.contains("transport.data.gouv.fr") -> "DataGouv / Etalab"
        h.contains("freshmile") -> "Freshmile"
        h.contains("atlante.energy") -> "Atlante"
        h.contains("char.gy") -> "CharGy UK"
        h.contains("vdl.lu") -> "Luxembourg VDL"
        h.contains("chargy.lu") -> "Chargy Luxembourg"
        h.contains("tankerkoenig") -> "Germany Tankerkoenig"
        h.contains("mityc.es") -> "Spain Minetur"
        h.contains("overpass") -> "OpenStreetMap / Overpass"
        h.contains("nominatim") -> "Nominatim Geocoding"
        h.contains("tile.openstreetmap.org") -> "OSM Map Tiles"
        h.contains("mimit.gov.it") -> "Italy Mimit"
        h.contains("dgeg.gov.pt") -> "Portugal DGEG"
        h.contains("fuelo.net") -> "Fuelo"
        h.contains("fastned") -> "Fastned"
        h.contains("eco-movement") -> "EcoMovement"
        h.contains("gireve") -> "Gireve"
        h.contains("qualicharge") -> "QualiCharge"
        h.contains("openvancamp") -> "OpenVanCamp"
        h.contains("comparis") || h.contains("gas-api.ch") -> "Switzerland Comparis"
        h.contains("weatherapi") || h.contains("open-meteo") -> "Weather API"
        h.contains("mapbox") -> "Mapbox"
        else -> null
    }
}

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
    val timestamp: Long,
    val requestSizeBytes: Long = 0,
    val responseSizeBytes: Long = 0,
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

    private val _totalBytesSent = MutableStateFlow(0L)
    val totalBytesSent: StateFlow<Long> = _totalBytesSent.asStateFlow()

    private val _totalBytesReceived = MutableStateFlow(0L)
    val totalBytesReceived: StateFlow<Long> = _totalBytesReceived.asStateFlow()

    private val _hostConsumption = MutableStateFlow<Map<String, HostDataConsumption>>(emptyMap())
    val hostConsumption: StateFlow<Map<String, HostDataConsumption>> = _hostConsumption.asStateFlow()

    private const val MAX_LOGS = 50

    fun addLog(log: NetworkLog) {
        _logs.update { current ->
            val next = current.toMutableList()
            next.add(0, log)
            if (next.size > MAX_LOGS) {
                val evicted = next.subList(MAX_LOGS, next.size)
                evicted.forEach { DebugLogPayloadCache.remove(it.id) }
                next.take(MAX_LOGS)
            } else {
                next
            }
        }
        _totalBytesSent.update { it + log.requestSizeBytes }
        _totalBytesReceived.update { it + log.responseSizeBytes }

        if (log.host.isNotBlank()) {
            _hostConsumption.update { currentMap ->
                val existing = currentMap[log.host] ?: HostDataConsumption(
                    host = log.host,
                    providerName = resolveProviderName(log.host)
                )
                val updated = existing.copy(
                    bytesSent = existing.bytesSent + log.requestSizeBytes,
                    bytesReceived = existing.bytesReceived + log.responseSizeBytes,
                    requestCount = existing.requestCount + 1
                )
                currentMap + (log.host to updated)
            }
        }
    }

    fun setTotalBytes(sent: Long, received: Long) {
        _totalBytesSent.value = sent
        _totalBytesReceived.value = received
    }

    fun resetDataConsumption() {
        _hostConsumption.value = emptyMap()
        _totalBytesSent.value = 0L
        _totalBytesReceived.value = 0L
    }

    fun clearLogs() {
        _logs.value = emptyList()
        DebugLogPayloadCache.clear()
    }

    fun clearAll() {
        clearLogs()
        ProviderTraceStore.clear()
        resetDataConsumption()
    }
}
