package fr.geoking.tools.debugbar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fr.geoking.tools.debugbar.model.CacheStats
import fr.geoking.tools.debugbar.model.HostDataConsumption
import fr.geoking.tools.debugbar.model.NetworkLog
import fr.geoking.tools.debugbar.model.ProviderTraceEntry
import kotlinx.serialization.json.JsonElement

@Composable
fun DebugLogOverlay(
    logs: List<NetworkLog> = emptyList(),
    providerTraces: Any? = null,
    hostConsumption: Any? = null,
    totalBytesSent: Long = 0L,
    totalBytesReceived: Long = 0L,
    disableCache: Boolean = false,
    onDisableCacheChange: (Boolean) -> Unit = {},
    onClearCaches: () -> Unit = {},
    cacheStats: CacheStats = CacheStats(),
    onRefreshCacheStats: () -> Unit = {},
    detectedCountries: Any? = null,
    customInfo: List<String>? = null,
    traces: List<ProviderTraceEntry> = emptyList(),
    dataConsumptions: List<HostDataConsumption> = emptyList(),
    onClearLogs: () -> Unit = {},
    onResetDataConsumption: () -> Unit = {},
    modifier: Modifier = Modifier,
) {}

@Composable
fun JsonTree(
    jsonString: String = "",
    jsonElement: JsonElement? = null,
    initialExpanded: Boolean = false,
    modifier: Modifier = Modifier,
) {}
