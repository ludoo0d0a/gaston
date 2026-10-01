package fr.geoking.tools.debugbar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fr.geoking.tools.debugbar.model.HostDataConsumption
import fr.geoking.tools.debugbar.model.NetworkLog
import fr.geoking.tools.debugbar.model.ProviderTraceEntry

@Composable
fun DebugLogOverlay(
    logs: List<NetworkLog>,
    providerTraces: List<ProviderTraceEntry>,
    hostConsumption: Map<String, HostDataConsumption>,
    totalBytesSent: Long,
    totalBytesReceived: Long,
    disableCache: Boolean,
    onDisableCacheChange: (Boolean) -> Unit,
    onClearCaches: () -> Unit,
    onClearLogs: () -> Unit,
    onResetDataConsumption: () -> Unit,
    modifier: Modifier = Modifier,
    detectedCountries: String? = null,
) {
    // Stub overlay when geoking-tools is not attached locally
}
