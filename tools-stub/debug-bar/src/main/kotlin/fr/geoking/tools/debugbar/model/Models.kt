package fr.geoking.tools.debugbar.model

data class CacheStatRow(
    val label: String = "",
    val itemCount: Int = 0,
    val name: String = "",
    val count: Int = 0,
    val sizeBytes: Long = 0L,
)

data class CacheStats(
    val label: String = "",
    val itemCount: Int = 0,
    val name: String = "",
    val count: Int = 0,
    val sizeBytes: Long = 0L,
    val rows: List<CacheStatRow> = emptyList(),
    val totalSizeBytes: Long = 0L,
    val totalItemCount: Int = 0,
    val byType: List<CacheStatRow> = emptyList(),
    val byHost: List<CacheStatRow> = emptyList(),
)

data class HostDataConsumption(
    val host: String = "",
    val providerName: String? = null,
    val bytesSent: Long = 0L,
    val bytesReceived: Long = 0L,
    val requestCount: Int = 0,
)

data class NetworkLog(
    val id: String = "",
    val timestamp: Long = 0L,
    val url: String = "",
    val host: String? = "",
    val method: String = "",
    val requestHeaders: Map<String, Any> = emptyMap(),
    val requestBody: String? = null,
    val responseCode: Int = 0,
    val statusCode: Int? = 0,
    val responseHeaders: Map<String, Any>? = emptyMap(),
    val responseBody: String? = null,
    val durationMs: Long = 0L,
    val requestSizeBytes: Long? = 0L,
    val responseSizeBytes: Long? = 0L,
)

enum class ProviderTracePhase {
    START, SUCCESS, ERROR, Resolved, CacheMemory, CacheDisk, FetchPlanned, FetchStart, FetchEnd, Skipped, Complete
}

data class ProviderTraceEntry(
    val id: String = "",
    val timestamp: Long = 0L,
    val providerName: String = "",
    val phase: ProviderTracePhase = ProviderTracePhase.START,
    val message: String = "",
    val details: Any? = null,
    val effectiveProviders: List<String> = emptyList(),
    val fetchedProviders: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val provider: String? = null,
    val poiCount: Int? = null,
    val durationMs: Long? = null,
    val errors: List<String> = emptyList(),
)
