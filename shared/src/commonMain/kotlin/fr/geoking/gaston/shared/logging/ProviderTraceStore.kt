package fr.geoking.gaston.shared.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
    val effectiveProviders: List<String> = emptyList(),
    val fetchedProviders: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val provider: String? = null,
    val poiCount: Int? = null,
    val durationMs: Long? = null,
    val errors: List<String> = emptyList(),
)

object ProviderTraceStore {
    private val _entries = MutableStateFlow<List<ProviderTraceEntry>>(emptyList())
    val entries: StateFlow<List<ProviderTraceEntry>> = _entries.asStateFlow()

    private const val MAX_ENTRIES = 80

    fun add(entry: ProviderTraceEntry) {
        _entries.update { current ->
            val next = current.toMutableList()
            next.add(0, entry)
            if (next.size > MAX_ENTRIES) next.take(MAX_ENTRIES) else next
        }
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
