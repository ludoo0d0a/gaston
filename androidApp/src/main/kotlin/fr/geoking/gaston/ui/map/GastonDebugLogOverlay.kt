package fr.geoking.gaston.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import fr.geoking.gaston.BuildConfig
import fr.geoking.gaston.CacheManager
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.countryCodesAtMapPosition
import fr.geoking.gaston.countryDisplayLabelAtMapPosition
import fr.geoking.gaston.shared.logging.DebugLogPayloadCache
import fr.geoking.gaston.shared.logging.DebugLogStore
import fr.geoking.gaston.shared.logging.ProviderTraceStore
import fr.geoking.tools.debugbar.DebugLogOverlay
import fr.geoking.tools.debugbar.model.CacheStats
import fr.geoking.tools.debugbar.model.HostDataConsumption
import fr.geoking.tools.debugbar.model.NetworkLog
import fr.geoking.tools.debugbar.model.ProviderTraceEntry
import fr.geoking.tools.debugbar.model.ProviderTracePhase
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import fr.geoking.gaston.shared.logging.HostDataConsumption as SharedHostDataConsumption
import fr.geoking.gaston.shared.logging.NetworkLog as SharedNetworkLog
import fr.geoking.gaston.shared.logging.ProviderTraceEntry as SharedProviderTraceEntry
import fr.geoking.gaston.shared.logging.ProviderTracePhase as SharedProviderTracePhase

/**
 * Gaston wiring for [DebugLogOverlay]: shared stores + Settings/CacheManager callbacks.
 */
@Composable
fun GastonDebugLogOverlay(
    modifier: Modifier = Modifier,
    detectedCountries: String? = null,
    onRefresh: (() -> Unit)? = null,
    customInfo: List<String>? = null,
) {
    val settingsManager = koinInject<SettingsManager>()
    val settings by settingsManager.settings.collectAsState()
    val logs by DebugLogStore.logs.collectAsState()
    val providerTraces by ProviderTraceStore.entries.collectAsState()
    val hostConsumptionMap by DebugLogStore.hostConsumption.collectAsState()
    val totalBytesSent by DebugLogStore.totalBytesSent.collectAsState()
    val totalBytesReceived by DebugLogStore.totalBytesReceived.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var cacheStatsTick by remember { mutableIntStateOf(0) }
    var cacheStats by remember { mutableStateOf(CacheStats()) }
    LaunchedEffect(cacheStatsTick) {
        cacheStats = CacheManager.collectStats(context)
    }

    val mappedLogs = remember(logs) { logs.map { it.toDebugBarLog() } }
    val mappedTraces = remember(providerTraces) { providerTraces.map { it.toDebugBarTrace() } }
    val mappedConsumption = remember(hostConsumptionMap) {
        hostConsumptionMap.mapValues { (_, v) -> v.toDebugBarHost() }
    }
    val positionCountries = remember(settings.lastKnownLat, settings.lastKnownLon) {
        val lat = settings.lastKnownLat
        val lon = settings.lastKnownLon
        if (lat == null || lon == null) {
            emptyList()
        } else {
            countryCodesAtMapPosition(lat, lon)
        }
    }
    val countriesLabel = remember(settings.lastKnownLat, settings.lastKnownLon, positionCountries) {
        val lat = settings.lastKnownLat
        val lon = settings.lastKnownLon
        when {
            lat == null || lon == null -> null
            positionCountries.isEmpty() -> "Unknown region"
            else -> countryDisplayLabelAtMapPosition(lat, lon)
        }
    }
    val infoLines = customInfo ?: remember(settings, positionCountries, countriesLabel) {
        buildList {
            add("app=gaston ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            add("flavor=${BuildConfig.FLAVOR} buildType=${BuildConfig.BUILD_TYPE}")
            val lat = settings.lastKnownLat
            val lon = settings.lastKnownLon
            if (lat != null && lon != null) {
                add("position=$lat,$lon")
            } else {
                add("position=(none)")
            }
            add("countries=${countriesLabel ?: "(none)"}")
            if (positionCountries.isNotEmpty()) {
                add("countryCodes=${positionCountries.joinToString()}")
            }
            add("disableCache=${settings.disableCache}")
            add("debugLogging=${settings.debugLoggingEnabled}")
            add("providers=${settings.selectedPoiProviders.sortedBy { it.name }.joinToString()}")
        }
    }

    DebugLogOverlay(
        logs = mappedLogs,
        providerTraces = mappedTraces,
        hostConsumption = mappedConsumption,
        totalBytesSent = totalBytesSent,
        totalBytesReceived = totalBytesReceived,
        disableCache = settings.disableCache,
        onDisableCacheChange = { settingsManager.setDisableCache(it) },
        onClearCaches = {
            scope.launch {
                CacheManager.clearAllCaches(context)
                cacheStatsTick++
                onRefresh?.invoke()
            }
        },
        onClearLogs = { DebugLogStore.clearAll() },
        onResetDataConsumption = { DebugLogStore.resetDataConsumption() },
        cacheStats = cacheStats,
        onRefreshCacheStats = { cacheStatsTick++ },
        modifier = modifier,
        detectedCountries = detectedCountries ?: countriesLabel,
        customInfo = infoLines,
    )
}

private fun SharedNetworkLog.toDebugBarLog(): NetworkLog {
    val reqBody = DebugLogPayloadCache.get(id, isRequest = true) ?: requestBody
    val respBody = DebugLogPayloadCache.get(id, isRequest = false) ?: responseBody
    return NetworkLog(
        id = id,
        url = url,
        host = host,
        method = method,
        requestHeaders = requestHeaders,
        requestBody = reqBody,
        responseHeaders = responseHeaders,
        responseBody = respBody,
        statusCode = statusCode,
        durationMs = durationMs,
        timestamp = timestamp,
        requestSizeBytes = requestSizeBytes,
        responseSizeBytes = responseSizeBytes,
    )
}

private fun SharedProviderTraceEntry.toDebugBarTrace(): ProviderTraceEntry =
    ProviderTraceEntry(
        id = id,
        timestamp = timestamp,
        phase = phase.toDebugBarPhase(),
        message = message,
        effectiveProviders = effectiveProviders,
        fetchedProviders = fetchedProviders,
        countries = countries,
        categories = categories,
        provider = provider,
        poiCount = poiCount,
        durationMs = durationMs,
        errors = errors,
    )

private fun SharedProviderTracePhase.toDebugBarPhase(): ProviderTracePhase =
    when (this) {
        SharedProviderTracePhase.Resolved -> ProviderTracePhase.Resolved
        SharedProviderTracePhase.CacheMemory -> ProviderTracePhase.CacheMemory
        SharedProviderTracePhase.CacheDisk -> ProviderTracePhase.CacheDisk
        SharedProviderTracePhase.FetchPlanned -> ProviderTracePhase.FetchPlanned
        SharedProviderTracePhase.FetchStart -> ProviderTracePhase.FetchStart
        SharedProviderTracePhase.FetchEnd -> ProviderTracePhase.FetchEnd
        SharedProviderTracePhase.Skipped -> ProviderTracePhase.Skipped
        SharedProviderTracePhase.Complete -> ProviderTracePhase.Complete
    }

private fun SharedHostDataConsumption.toDebugBarHost(): HostDataConsumption =
    HostDataConsumption(
        host = host,
        providerName = providerName,
        bytesSent = bytesSent,
        bytesReceived = bytesReceived,
        requestCount = requestCount,
    )
