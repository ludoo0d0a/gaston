package fr.geoking.gaston.ui.map

import fr.geoking.gaston.R
import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.gaston.CacheManager
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.api.belib.BorneAvailabilityProviderFactory
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.api.belib.matchAvailabilityToPois
import fr.geoking.gaston.api.traffic.TrafficInfo
import fr.geoking.gaston.api.traffic.TrafficProviderFactory
import fr.geoking.gaston.api.traffic.TrafficRequest
import fr.geoking.gaston.community.FavoritesRepository
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiMerger
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.poi.PoiSearchRequest
import fr.geoking.gaston.poi.radiusKmFromMapViewport
import fr.geoking.gaston.poi.calculateBoundsFromMapViewport
import fr.geoking.gaston.shared.diagnostics.DiagnosticStore
import fr.geoking.gaston.shared.location.approxDistanceKm
import fr.geoking.gaston.shared.network.NetworkException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlin.math.abs

@Stable
data class MapCameraSample(
    val centerLat: Double,
    val centerLon: Double,
    val zoom: Float
)

/** True when [next] is close enough that cancelling an in-flight POI search would be wasteful. */
private fun isNearSameViewport(current: MapCameraSample, next: MapCameraSample): Boolean {
    val latClose = abs(current.centerLat - next.centerLat) < 0.01
    val lonClose = abs(current.centerLon - next.centerLon) < 0.01
    val zoomClose = abs(current.zoom - next.zoom) < 0.35f
    return latClose && lonClose && zoomClose
}

@Stable
data class MapDataState(
    val cachedPois: List<Poi>,
    val availabilityByPoiId: Map<String, StationAvailabilitySummary>,
    val trafficInfo: TrafficInfo?,
    val isLoading: Boolean,
    val mapErrorMessage: String?,
    val isErrorPaused: Boolean,
    val retryCount: Int
)

@Composable
fun rememberMapDataState(
    context: Context,
    poiProvider: PoiProvider,
    availabilityProviderFactory: BorneAvailabilityProviderFactory?,
    trafficProviderFactory: TrafficProviderFactory?,
    settingsManager: SettingsManager,
    diagnostics: DiagnosticStore,
    effectiveProvidersLabel: String,
    initialSelectedPoi: Poi?,
    cameraFlow: Flow<MapCameraSample>,
    mapWidthPx: Int,
    mapHeightPx: Int,
    isLocationPermissionGranted: Boolean,
    requestLocationPermission: () -> Unit
): Pair<MapDataState, MapDataActions> {
    var cachedPois by remember { mutableStateOf<List<Poi>>(initialSelectedPoi?.let { listOf(it) } ?: emptyList()) }
    var availabilityByPoiId by remember { mutableStateOf<Map<String, StationAvailabilitySummary>>(emptyMap()) }
    var trafficInfo by remember { mutableStateOf<TrafficInfo?>(null) }
    var mapErrorMessage by remember { mutableStateOf<String?>(null) }
    var isErrorPaused by remember { mutableStateOf(false) }
    var retryCount by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var lastCameraSample by remember { mutableStateOf<MapCameraSample?>(null) }
    val refreshRequestFlow = remember { MutableSharedFlow<MapCameraSample>(extraBufferCapacity = 1) }

    val scope = rememberCoroutineScope()

    val actions = remember(context) {
        MapDataActions(
            refresh = { _, _ -> },
            clearError = { },
            retry = { _ -> },
            invalidate = { }
        )
    }
    actions.refresh = { clearCaches, atCenter ->
        scope.launch {
            if (clearCaches) {
                CacheManager.clearAllCaches(context)
                poiProvider.clearCache()
            }
            cachedPois = emptyList()
            availabilityByPoiId = emptyMap()
            trafficInfo = null
            mapErrorMessage = null
            isErrorPaused = false
            retryCount++
            val sample = atCenter ?: lastCameraSample
            if (sample != null) {
                refreshRequestFlow.emit(sample)
            }
        }
    }
    actions.clearError = {
        mapErrorMessage = null
    }
    actions.retry = { atCenter ->
        mapErrorMessage = null
        isErrorPaused = false
        scope.launch {
            retryCount++
            val sample = atCenter ?: lastCameraSample
            if (sample != null) {
                refreshRequestFlow.emit(sample)
            }
        }
    }
    actions.invalidate = {
        retryCount++
    }

    // Read latest map size inside the collector; do not key LaunchedEffect on size.
    // Tiny height changes (loader, insets) used to cancel+restart search in a loop.
    val latestMapWidthPx by rememberUpdatedState(mapWidthPx)
    val latestMapHeightPx by rememberUpdatedState(mapHeightPx)

    // Phone map used to only re-search on camera moves — switching Other/speed_camera
    // (Lufop) never triggered api.lufop.net until the user panned. Mirror Auto screens.
    LaunchedEffect(settingsManager) {
        settingsManager.settings
            .map { s ->
                listOf(
                    s.poiProviderSelectionMode.name,
                    s.selectedPoiProviders.sortedBy { it.name }.joinToString(","),
                    s.selectedOverpassAmenityTypes.sorted().joinToString(","),
                    s.mapEnergyMode.name,
                    s.useVehicleFilter.toString(),
                    s.disableCache.toString(),
                    s.bulkFileDownloadPolicy.name,
                ).joinToString("|")
            }
            .distinctUntilChanged()
            .drop(1)
            .collect {
                val sample = lastCameraSample ?: return@collect
                isErrorPaused = false
                mapErrorMessage = null
                refreshRequestFlow.emit(sample)
            }
    }

    LaunchedEffect(retryCount, isLocationPermissionGranted, cameraFlow) {
        if (!isLocationPermissionGranted) {
            requestLocationPermission()
        }

        snapshotFlow { latestMapWidthPx to latestMapHeightPx }
            .first { (w, h) -> w > 0 && h > 0 }

        // Avoid collectLatest: cancelling mid-flight dropped slower Lufop once Overpass/camera
        // re-emitted. Let nearby in-flight searches finish; only restart on a real move/refresh.
        var searchJob: Job? = null
        var searchingSample: MapCameraSample? = null

        merge(cameraFlow, refreshRequestFlow).collect { sample ->
            lastCameraSample = sample
            if (isErrorPaused) return@collect

            val inFlight = searchingSample
            if (searchJob?.isActive == true && inFlight != null && isNearSameViewport(inFlight, sample)) {
                return@collect
            }

            searchJob?.cancel()
            searchingSample = sample
            searchJob = launch {
                val widthPx = latestMapWidthPx
                val heightPx = latestMapHeightPx
                if (widthPx <= 0 || heightPx <= 0) return@launch

                val centerLat = sample.centerLat
                val centerLng = sample.centerLon
                val zoom = sample.zoom

                val viewport = calculateBoundsFromMapViewport(
                    centerLat,
                    centerLng,
                    zoom,
                    widthPx,
                    heightPx
                )

                val requiredRadiusKm = radiusKmFromMapViewport(
                    centerLat,
                    centerLng,
                    viewport
                ).coerceIn(1, 50)

                mapErrorMessage = null

                try {
                    isLoading = true
                    poiProvider.searchFlow(
                        PoiSearchRequest(
                            latitude = centerLat,
                            longitude = centerLng,
                            viewport = viewport,
                            categories = emptySet(),
                            skipFilters = true
                        )
                    ).collect { result ->
                        if (result.errors.isEmpty() || result.pois.isNotEmpty()) {
                            cachedPois = trimMapPois(
                                PoiMerger.mergeInto(cachedPois, result.pois),
                                centerLat,
                                centerLng,
                            )
                            // Partial provider failures (e.g. Lufop) must not freeze later searches
                            // once another source (Overpass) has returned pins.
                            if (cachedPois.isNotEmpty()) {
                                isErrorPaused = false
                                mapErrorMessage = null
                            }

                            val availabilityProvider = availabilityProviderFactory?.getProvider(centerLat, centerLng)
                            if (availabilityProvider != null) {
                                val availabilityRadiusKm = requiredRadiusKm.coerceAtMost(20).coerceAtLeast(10)
                                val availabilities = availabilityProvider.getAvailability(centerLat, centerLng, availabilityRadiusKm)
                                val poisForAvailability = cachedPois.filter { poi ->
                                    approxDistanceKm(centerLat, centerLng, poi.latitude, poi.longitude) <= availabilityRadiusKm * 1.05
                                }
                                val matched = matchAvailabilityToPois(availabilities, poisForAvailability)
                                availabilityByPoiId = matched
                            }
                        }

                        // Only pause when every provider failed and the map still has nothing to show.
                        if (result.errors.isNotEmpty() && result.pois.isEmpty() && cachedPois.isEmpty()) {
                            val firstError = result.errors.first()
                            val msg = firstError.message
                            val code = firstError.httpCode

                            mapErrorMessage = msg
                            isErrorPaused = true
                            diagnostics.recordError(code, "Map ($effectiveProvidersLabel): $msg")
                        } else if (result.errors.isNotEmpty()) {
                            // Keep searching; surface first error without locking the map.
                            result.errors.firstOrNull()?.let { err ->
                                diagnostics.recordError(err.httpCode, "Map ($effectiveProvidersLabel): ${err.message}")
                            }
                        }
                    }

                    val trafficProvider = trafficProviderFactory?.getProvider(centerLat, centerLng)
                    trafficInfo = if (trafficProvider != null) {
                        val halfSpan = 0.15
                        trafficProvider.getTraffic(
                            TrafficRequest.Bbox(
                                centerLat - halfSpan,
                                centerLng - halfSpan,
                                centerLat + halfSpan,
                                centerLng + halfSpan
                            )
                        )
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    val msg = e.message?.takeIf { it.isNotBlank() } ?: e.toString()
                    mapErrorMessage = msg
                    isErrorPaused = true
                    diagnostics.recordError(
                        (e as? NetworkException)?.httpCode,
                        "Map ($effectiveProvidersLabel): $msg"
                    )
                } finally {
                    isLoading = false
                    if (searchingSample == sample) searchingSample = null
                }
            }
        }
    }

    return MapDataState(
        cachedPois = cachedPois,
        availabilityByPoiId = availabilityByPoiId,
        trafficInfo = trafficInfo,
        isLoading = isLoading,
        mapErrorMessage = mapErrorMessage,
        isErrorPaused = isErrorPaused,
        retryCount = retryCount
    ) to actions
}

@Stable
class MapDataActions(
    refresh: (clearCaches: Boolean, atCenter: MapCameraSample?) -> Unit,
    clearError: () -> Unit,
    retry: (atCenter: MapCameraSample?) -> Unit,
    invalidate: () -> Unit
) {
    var refresh: (clearCaches: Boolean, atCenter: MapCameraSample?) -> Unit = refresh
    var clearError: () -> Unit = clearError
    var retry: (atCenter: MapCameraSample?) -> Unit = retry
    var invalidate: () -> Unit = invalidate
}

@Composable
fun MapErrorBanner(
    message: String,
    onCopy: () -> Unit,
    onViewFullError: () -> Unit,
    onIgnore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val maxHeight = configuration.screenHeightDp.dp * 0.15f
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight),
        color = MaterialTheme.colorScheme.errorContainer,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCopy) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.action_copy),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onViewFullError) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = stringResource(R.string.action_view_full_error),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onRetry) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.action_retry),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onIgnore) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_ignore),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private const val MAX_MAP_POIS = 200

private fun trimMapPois(pois: List<Poi>, lat: Double, lng: Double): List<Poi> {
    if (pois.size <= MAX_MAP_POIS) return pois
    return pois.sortedBy { approxDistanceKm(lat, lng, it.latitude, it.longitude) }.take(MAX_MAP_POIS)
}

@Composable
fun rememberErrorClipboardCopyHandler(message: String): () -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return remember(message) {
        {
            scope.launch {
                clipboard.setClipEntry(ClipEntry(android.content.ClipData.newPlainText("error", message)))
            }
        }
    }
}

