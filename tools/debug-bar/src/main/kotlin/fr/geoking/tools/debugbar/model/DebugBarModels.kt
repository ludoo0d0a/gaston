package fr.geoking.tools.debugbar.model

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
)

enum class ProviderTracePhase {
    Resolved,
    CacheMemory,
    CacheDisk,
    FetchPlanned,
    FetchStart,
    FetchEnd,
    Skipped,
    Complete,
}

data class ProviderTraceEntry(
    val id: String,
    val timestamp: Long,
    val phase: ProviderTracePhase,
    val message: String,
    val effectiveProviders: List<String>? = null,
    val fetchedProviders: List<String>? = null,
    val countries: List<String>? = null,
    val categories: List<String>? = null,
    val provider: String? = null,
    val poiCount: Int? = null,
    val durationMs: Long? = null,
    val errors: List<String>? = null,
)

data class HostDataConsumption(
    val host: String,
    val providerName: String?,
    val bytesSent: Long,
    val bytesReceived: Long,
    val requestCount: Int,
)
