package fr.geoking.gaston.ui

import android.graphics.Rect
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.StationMapFilters
import fr.geoking.gaston.auto.AutoMapCamera
import fr.geoking.gaston.auto.AutoMapHeading
import fr.geoking.gaston.auto.AutoSurfaceRenderer
import fr.geoking.gaston.auto.MapOrientationMode
import fr.geoking.gaston.auto.AutoMapFollowFocalPoint
import fr.geoking.gaston.effectiveIrvePowerLevels
import fr.geoking.gaston.effectiveMapEnergyFilterIds
import fr.geoking.gaston.effectiveProvidersAt
import fr.geoking.gaston.feature.location.LocationHelper
import fr.geoking.gaston.poi.FuelPrice
import fr.geoking.gaston.poi.MapPoiFilter
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiMerger
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.poi.PoiSearchRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * A highly interactive screen on phone to reuse and debug the mechanism
 * of Android Auto's custom map rendering, boundary constraints, rotation, and zooming.
 *
 * Supports both portrait and landscape, mimics AA floating menus and action strip,
 * and allows real-time pan/drag, rotation adjustment, visible area cropping,
 * custom scrollbars, loading progress displays, and simulated travel/itinerary following.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoDebugScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by settingsManager.settings.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Inject Koin components
    val poiProvider = koinInject<PoiProvider>()

    // Map States
    var mapLat by remember { mutableStateOf(48.8566) } // Paris Center
    var mapLon by remember { mutableStateOf(2.3522) }
    var zoom by remember { mutableStateOf(14) }
    var bearing by remember { mutableStateOf(0f) }
    var orientationMode by remember { mutableStateOf(MapOrientationMode.NorthUp) }
    var visibleAreaEnabled by remember { mutableStateOf(false) }
    var mapTileDebugEnabled by remember { mutableStateOf(true) }

    // Current user location state (for blue location arrow/dot)
    var userLat by remember { mutableStateOf<Double?>(null) }
    var userLon by remember { mutableStateOf<Double?>(null) }

    // UI controls visibility states
    var showRotationSlider by remember { mutableStateOf(true) }
    var showStationsBottomSheet by remember { mutableStateOf(true) }
    var isFabMenuExpanded by remember { mutableStateOf(false) }

    val scaffoldState = rememberBottomSheetScaffoldState()

    // Real POIs state loaded via poiProvider
    var loadedPois by remember { mutableStateOf<List<Poi>>(emptyList()) }
    var isQueryPending by remember { mutableStateOf(false) }

    // Travel Simulation states
    var isSimulatingTravel by remember { mutableStateOf(false) }
    var travelPath by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }
    var currentPathIndex by remember { mutableStateOf(0) }
    var historyPoints by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }

    // Selected POI
    var selectedPoiId by remember { mutableStateOf<String?>(null) }

    // Retrieve initial/current location
    LaunchedEffect(Unit) {
        val (lat, lon) = LocationHelper.getInitialLocation(context, settingsManager)
        userLat = lat
        userLon = lon
        // Set map camera initial center to the user's location
        mapLat = lat
        mapLon = lon
    }

    // Mock POIs dynamically generated around current location or Paris
    val mockPois = remember(userLat, userLon) {
        val baseLat = userLat ?: 48.8566
        val baseLon = userLon ?: 2.3522
        listOf(
            Poi(
                id = "mock_station_1",
                name = "TotalEnergies Paris Center",
                latitude = baseLat + 0.0018,
                longitude = baseLon + 0.0028,
                address = "12 Rue de Rivoli, Paris",
                isElectric = false,
                poiCategory = PoiCategory.Gas,
                fuelPrices = listOf(
                    FuelPrice("Gazole", 1.849, "TotalEnergies"),
                    FuelPrice("SP95", 1.919, "TotalEnergies")
                ),
                brand = "Total"
            ),
            Poi(
                id = "mock_station_2",
                name = "Chargy Paris Nord",
                latitude = baseLat + 0.0084,
                longitude = baseLon,
                address = "85 Boulevard de Sébastopol, Paris",
                isElectric = true,
                poiCategory = PoiCategory.Irve,
                brand = "Chargy"
            ),
            Poi(
                id = "mock_station_3",
                name = "Tesla Supercharger Châtelet",
                latitude = baseLat + 0.0024,
                longitude = baseLon - 0.0112,
                address = "Place du Châtelet, Paris",
                isElectric = true,
                poiCategory = PoiCategory.Irve,
                brand = "Tesla"
            )
        )
    }

    // Reference to the surface renderer
    var surfaceRendererRef by remember { mutableStateOf<AutoSurfaceRenderer?>(null) }
    var surfaceWidth by remember { mutableStateOf(0) }
    var surfaceHeight by remember { mutableStateOf(0) }

    // Current effective visible area boundary (pixel coordinates)
    val visibleAreaRect = remember(surfaceWidth, surfaceHeight, visibleAreaEnabled) {
        if (visibleAreaEnabled && surfaceWidth > 0 && surfaceHeight > 0) {
            // Emulate a left menu cropping 300px from left
            Rect(320, 40, surfaceWidth - 40, surfaceHeight - 40)
        } else {
            null
        }
    }

    // Query actual POIs dynamically based on map position with debounce
    var activeSearchJob by remember { mutableStateOf<Job?>(null) }
    fun triggerPoiSearch(lat: Double, lon: Double) {
        activeSearchJob?.cancel()
        activeSearchJob = coroutineScope.launch {
            isQueryPending = true
            try {
                val viewport = AutoMapCamera.searchViewportOrNull(
                    centerLat = lat,
                    centerLon = lon,
                    zoom = zoom,
                    mapWidthPx = surfaceWidth.coerceAtLeast(800),
                    mapHeightPx = surfaceHeight.coerceAtLeast(480)
                )
                poiProvider.searchFlow(
                    PoiSearchRequest(
                        latitude = lat,
                        longitude = lon,
                        viewport = viewport,
                        categories = emptySet(),
                        skipFilters = true,
                    )
                ).collect { result ->
                    // Merge new search results into the current list of loaded POIs
                    loadedPois = PoiMerger.mergeInto(loadedPois, result.pois)
                }
            } catch (e: Exception) {
                Log.e("AutoDebugScreen", "POI search failed", e)
            } finally {
                isQueryPending = false
            }
        }
    }

    // Trigger search when camera centers on a new location (with a minor debounce)
    LaunchedEffect(mapLat, mapLon, zoom) {
        delay(300)
        triggerPoiSearch(mapLat, mapLon)
    }

    // Merge mock POIs and real loaded POIs using the shared PoiMerger component
    val allPois = remember(mockPois, loadedPois) {
        PoiMerger.mergeInto(mockPois, loadedPois)
    }

    // Apply the standard filtering rules as defined in the shared module
    val filteredPois = remember(allPois, settings, mapLat, mapLon) {
        val providers = settings.effectiveProvidersAt(mapLat, mapLon)
        StationMapFilters.apply(
            settings = settings,
            pois = allPois,
            providers = providers,
            skipWhenOnlyOverpass = true
        )
    }

    // Apply sorting to match the stations display behavior in AA
    val sortedPois = remember(filteredPois, mapLat, mapLon) {
        MapPoiFilter.sortPois(
            pois = filteredPois,
            lat = mapLat,
            lon = mapLon,
            sortByPrice = false,
            selectedFuelIds = settings.effectiveMapEnergyFilterIds() - "electric"
        )
    }

    val selectedPoi = sortedPois.find { it.id == selectedPoiId }

    // Travel Simulation Itinerary follow up loop
    LaunchedEffect(isSimulatingTravel, currentPathIndex) {
        if (isSimulatingTravel && currentPathIndex < travelPath.size) {
            val point = travelPath[currentPathIndex]

            // Update user simulated location and center map
            userLat = point.first
            userLon = point.second
            mapLat = point.first
            mapLon = point.second

            // Compute travel bearing/direction of driving to rotate map
            if (currentPathIndex < travelPath.size - 1) {
                val nextPoint = travelPath[currentPathIndex + 1]
                val dy = nextPoint.first - point.first
                val dx = nextPoint.second - point.second
                val angleRad = atan2(dy, dx)
                // Convert to clockwise degrees starting from North
                var brng = 90f - Math.toDegrees(angleRad).toFloat()
                if (brng < 0) brng += 360f
                bearing = brng
            }

            // Append to history list
            historyPoints = historyPoints + point

            delay(1500)
            if (currentPathIndex < travelPath.size - 1) {
                currentPathIndex++
            } else {
                isSimulatingTravel = false
            }
        }
    }

    // Push states to renderer when updated
    LaunchedEffect(mapLat, mapLon, zoom, bearing, orientationMode, visibleAreaRect, selectedPoiId, userLat, userLon, mapTileDebugEnabled, sortedPois, isQueryPending, travelPath, historyPoints) {
        val renderer = surfaceRendererRef ?: return@LaunchedEffect
        renderer.updateLocation(mapLat, mapLon, zoom)
        renderer.setMapOrientation(orientationMode, bearing)
        renderer.setMapTileDebugEnabled(mapTileDebugEnabled)
        if (visibleAreaRect != null) {
            renderer.updateVisibleArea(visibleAreaRect)
        } else {
            renderer.updateVisibleArea(Rect(0, 0, surfaceWidth, surfaceHeight))
        }
        val uLat = userLat
        val uLon = userLon
        if (uLat != null && uLon != null) {
            // Same as CustomMapPoiScreen: user arrow uses the current heading.
            renderer.updateUserLocation(uLat, uLon, bearing)
        }

        // Draw red circle loading zone around current map center matching visible screen bounds
        val radiusKm = AutoMapCamera.searchRadiusKm(
            centerLat = mapLat,
            centerLon = mapLon,
            zoom = zoom,
            mapWidthPx = surfaceWidth.coerceAtLeast(800),
            mapHeightPx = surfaceHeight.coerceAtLeast(480)
        ).toDouble()
        renderer.updateSearchRadius(mapLat, mapLon, radiusKm)

        // Pass the loader state
        renderer.setQueryPending(isQueryPending)

        // Sync history and itinerary routing lines
        renderer.setItinerary(travelPath)
        renderer.setHistory(historyPoints)

        // Update markers display
        renderer.updatePois(
            newPois = sortedPois,
            effectiveEnergyTypes = settings.effectiveMapEnergyFilterIds(),
            effectivePowerLevels = settings.effectiveIrvePowerLevels(),
            selectedId = selectedPoiId
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AA Map Surface Debug") },
                navigationIcon = {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("Back") } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    // 1. Simulate Menu Action
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("Simulate Menu") } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(
                            onClick = { visibleAreaEnabled = !visibleAreaEnabled },
                            modifier = Modifier.testTag("toggle_visible_area_btn")
                        ) {
                            Icon(
                                imageVector = if (visibleAreaEnabled) Icons.Default.MenuOpen else Icons.Default.Menu,
                                contentDescription = "Simulate Menu",
                                tint = if (visibleAreaEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 2. North-Up Toggle Action
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("Toggle Orientation") } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(
                            onClick = {
                                orientationMode = if (orientationMode == MapOrientationMode.NorthUp) {
                                    MapOrientationMode.HeadingUp
                                } else {
                                    MapOrientationMode.NorthUp
                                }
                            },
                            modifier = Modifier.testTag("toggle_orientation_mode_btn")
                        ) {
                            Icon(
                                imageVector = if (orientationMode == MapOrientationMode.NorthUp) Icons.Default.North else Icons.Default.Navigation,
                                contentDescription = "Toggle Orientation",
                                tint = if (orientationMode == MapOrientationMode.NorthUp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    // 3. Simulate Travel Action
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("Simulate Travel") } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(
                            onClick = {
                                if (isSimulatingTravel) {
                                    isSimulatingTravel = false
                                } else {
                                    val startLat = userLat ?: mapLat
                                    val startLon = userLon ?: mapLon
                                    // Generate winding short travel path
                                    val path = mutableListOf<Pair<Double, Double>>()
                                    path.add(startLat to startLon)
                                    val angleRad = Math.random() * 2 * Math.PI
                                    val steps = 15
                                    val stepDegrees = 0.015 / steps
                                    var currentLat = startLat
                                    var currentLon = startLon
                                    for (i in 1..steps) {
                                        val turnAngle = (Math.random() - 0.5) * (Math.PI / 3)
                                        val currentAngle = angleRad + turnAngle
                                        currentLat += stepDegrees * cos(currentAngle)
                                        currentLon += stepDegrees * sin(currentAngle)
                                        path.add(currentLat to currentLon)
                                    }
                                    travelPath = path
                                    historyPoints = emptyList()
                                    currentPathIndex = 0
                                    orientationMode = MapOrientationMode.HeadingUp
                                    isSimulatingTravel = true
                                }
                            },
                            modifier = Modifier.testTag("toggle_simulation_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = "Simulate Travel",
                                tint = if (isSimulatingTravel) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 4. Map Tile Debug Grid Action
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("Map Tile Debug Grid") } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(
                            onClick = { mapTileDebugEnabled = !mapTileDebugEnabled },
                            modifier = Modifier.testTag("toggle_map_tile_debug_btn")
                        ) {
                            Icon(
                                imageVector = if (mapTileDebugEnabled) Icons.Default.GridOn else Icons.Default.GridOff,
                                contentDescription = "Map Tile Debug Grid",
                                tint = if (mapTileDebugEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { paddingValues ->
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = if (showStationsBottomSheet) 140.dp else 0.dp,
            sheetContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
            containerColor = Color.Transparent,
            sheetContent = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (selectedPoi == null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Stations List (${sortedPois.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (isQueryPending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }

                        val listState = rememberLazyListState()
                        LazyColumnWithScrollbar(
                            state = listState,
                            modifier = Modifier.weight(1f)
                        ) {
                            items(sortedPois) { poi ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedPoiId = poi.id },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    ListItem(
                                        headlineContent = { Text(poi.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        supportingContent = { Text(poi.address, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        leadingContent = {
                                            Icon(
                                                imageVector = if (poi.isElectric) Icons.Default.EvStation else Icons.Default.LocalGasStation,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Station Detail View",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { selectedPoiId = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Close Detail")
                            }
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    selectedPoi.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(selectedPoi.address, style = MaterialTheme.typography.bodyMedium)

                                val prices = selectedPoi.fuelPrices
                                if (!prices.isNullOrEmpty()) {
                                    Text("Fuel Prices:", fontWeight = FontWeight.Bold)
                                    prices.forEach { price ->
                                        Text(" • ${price.fuelName}: €${price.price}")
                                    }
                                } else if (selectedPoi.isElectric) {
                                    Text("EV Charging Details:", fontWeight = FontWeight.Bold)
                                    Text(" • Type 2, CCS connectors available")
                                    Text(" • Dynamic availability supported")
                                }
                            }
                        }
                    }
                }
            }
        ) { sheetPaddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                MapSurfaceAndControls(
                    zoom = zoom,
                    bearing = bearing,
                    mapLat = mapLat,
                    mapLon = mapLon,
                    userLat = userLat,
                    userLon = userLon,
                    orientationMode = orientationMode,
                    onZoomChange = { zoom = it },
                    onBearingChange = { newBearing ->
                        bearing = newBearing
                        orientationMode = AutoMapHeading.modeAfterBearingChange(
                            orientationMode,
                            newBearing,
                        )
                    },
                    onMapPan = { dLat, dLon ->
                        mapLat += dLat
                        mapLon += dLon
                    },
                    visibleAreaRect = visibleAreaRect,
                    bottomPadding = sheetPaddingValues.calculateBottomPadding(),
                    showRotationSlider = showRotationSlider,
                    showStationsBottomSheet = showStationsBottomSheet,
                    onToggleRotationSlider = { showRotationSlider = !showRotationSlider },
                    onToggleStationsBottomSheet = {
                        showStationsBottomSheet = !showStationsBottomSheet
                        if (!showStationsBottomSheet) {
                            coroutineScope.launch {
                                scaffoldState.bottomSheetState.partialExpand()
                            }
                        }
                    },
                    isFabMenuExpanded = isFabMenuExpanded,
                    onToggleFabMenuExpanded = { isFabMenuExpanded = !isFabMenuExpanded },
                    onSurfaceCreated = { renderer, w, h ->
                        surfaceRendererRef = renderer
                        surfaceWidth = w
                        surfaceHeight = h
                    },
                    onSurfaceDestroyed = {
                        surfaceRendererRef = null
                    }
                )
            }
        }
    }
}

/**
 * A custom component that displays a standard LazyColumn alongside a scrollbar indicator.
 */
@Composable
fun LazyColumnWithScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit
) {
    Box(modifier = modifier) {
        LazyColumn(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )

        val layoutInfo = state.layoutInfo
        val totalItemsCount = layoutInfo.totalItemsCount
        val visibleItemsInfo = layoutInfo.visibleItemsInfo
        if (totalItemsCount > 0 && visibleItemsInfo.isNotEmpty()) {
            val firstVisibleItem = visibleItemsInfo.first()
            val visibleItemsCount = visibleItemsInfo.size
            if (visibleItemsCount < totalItemsCount) {
                val scrollFraction = firstVisibleItem.index.toFloat() / (totalItemsCount - visibleItemsCount)
                val thumbHeightFraction = visibleItemsCount.toFloat() / totalItemsCount

                BoxWithConstraints(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                ) {
                    val trackHeight = maxHeight
                    val thumbHeight = trackHeight * thumbHeightFraction
                    val thumbOffset = (trackHeight - thumbHeight) * scrollFraction

                    Box(
                        modifier = Modifier
                            .offset(y = thumbOffset)
                            .size(width = 4.dp, height = thumbHeight)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}

@Composable
fun MapSurfaceAndControls(
    zoom: Int,
    bearing: Float,
    mapLat: Double,
    mapLon: Double,
    userLat: Double?,
    userLon: Double?,
    orientationMode: MapOrientationMode,
    onZoomChange: (Int) -> Unit,
    onBearingChange: (Float) -> Unit,
    onMapPan: (Double, Double) -> Unit,
    visibleAreaRect: Rect?,
    bottomPadding: androidx.compose.ui.unit.Dp,
    showRotationSlider: Boolean,
    showStationsBottomSheet: Boolean,
    onToggleRotationSlider: () -> Unit,
    onToggleStationsBottomSheet: () -> Unit,
    isFabMenuExpanded: Boolean,
    onToggleFabMenuExpanded: () -> Unit,
    onSurfaceCreated: (AutoSurfaceRenderer, Int, Int) -> Unit,
    onSurfaceDestroyed: () -> Unit
) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        // SurfaceView host for AutoSurfaceRenderer
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(zoom, bearing, mapLat, mapLon) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()

                        // Convert screen dragAmount pixels to map latitude and longitude changes.
                        // At zoom z, the world is 256 * 2^z pixels.
                        val degreesPerPixelX = 360.0 / (256.0 * (1 shl zoom))
                        val latRad = Math.toRadians(mapLat)
                        val degreesPerPixelY = degreesPerPixelX * cos(latRad)

                        // Rotate the drag vector by current bearing to align panning with the screen's rotation.
                        val bearingRad = Math.toRadians(bearing.toDouble())
                        val cosB = cos(bearingRad)
                        val sinB = sin(bearingRad)
                        val rotatedDragX = dragAmount.x * cosB - dragAmount.y * sinB
                        val rotatedDragY = dragAmount.x * sinB + dragAmount.y * cosB

                        val dLon = -rotatedDragX * degreesPerPixelX
                        val dLat = rotatedDragY * degreesPerPixelY

                        onMapPan(dLat, dLon)
                    }
                },
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        var renderer: AutoSurfaceRenderer? = null

                        override fun surfaceCreated(holder: SurfaceHolder) {
                            // Handled in surfaceChanged
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int
                        ) {
                            renderer?.stop()
                            val r = AutoSurfaceRenderer(
                                context = ctx,
                                surface = holder.surface,
                                width = width,
                                height = height
                            )
                            renderer = r
                            onSurfaceCreated(r, width, height)
                            r.start()
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            renderer?.stop()
                            renderer = null
                            onSurfaceDestroyed()
                        }
                    })
                }
            }
        )

        // Overlay Canvas for Simulated Boundary (Visible Area) and Red Circle Focal Point
        Canvas(modifier = Modifier.fillMaxSize()) {
            val surfaceWidth = size.width.toInt()
            val surfaceHeight = size.height.toInt()

            if (visibleAreaRect != null) {
                val left = visibleAreaRect.left.toFloat()
                val top = visibleAreaRect.top.toFloat()
                val right = visibleAreaRect.right.toFloat()
                val bottom = visibleAreaRect.bottom.toFloat()

                // Draw a clear dashed red border around the simulated visible area
                drawRect(
                    color = Color.Red,
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                    style = Stroke(
                        width = 4f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                    )
                )

                // Dim the outer boundary area slightly
                // Left strip
                drawRect(
                    color = Color.Black.copy(alpha = 0.2f),
                    topLeft = Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(left, size.height)
                )
                // Right strip
                drawRect(
                    color = Color.Black.copy(alpha = 0.2f),
                    topLeft = Offset(right, 0f),
                    size = androidx.compose.ui.geometry.Size(size.width - right, size.height)
                )
                // Top strip (between left and right)
                drawRect(
                    color = Color.Black.copy(alpha = 0.2f),
                    topLeft = Offset(left, 0f),
                    size = androidx.compose.ui.geometry.Size(right - left, top)
                )
                // Bottom strip (between left and right)
                drawRect(
                    color = Color.Black.copy(alpha = 0.2f),
                    topLeft = Offset(left, bottom),
                    size = androidx.compose.ui.geometry.Size(right - left, size.height - bottom)
                )
            }

            // Always draw the Red Circle at the Focal Point
            if (surfaceWidth > 0 && surfaceHeight > 0) {
                val focalPoint = AutoMapFollowFocalPoint.focalPointPx(
                    visibleArea = visibleAreaRect,
                    surfaceWidth = surfaceWidth,
                    surfaceHeight = surfaceHeight,
                    headingUp = (orientationMode == MapOrientationMode.HeadingUp)
                )

                // Outer red circle
                drawCircle(
                    color = Color.Red,
                    radius = 24f,
                    center = Offset(focalPoint.x.toFloat(), focalPoint.y.toFloat()),
                    style = Stroke(width = 4f)
                )

                // Inner red dot
                drawCircle(
                    color = Color.Red,
                    radius = 6f,
                    center = Offset(focalPoint.x.toFloat(), focalPoint.y.toFloat())
                )
            }
        }

        // Action Strip Mimic (Top Right Floating UI)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Compass / Reset bearing button
            FloatingActionButton(
                onClick = { onBearingChange(0f) },
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                modifier = Modifier.size(48.dp).testTag("action_strip_compass_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = "Compass",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.rotate(bearing)
                )
            }

            // Zoom In
            FloatingActionButton(
                onClick = { if (zoom < 18) onZoomChange(zoom + 1) },
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                modifier = Modifier.size(48.dp).testTag("action_strip_zoomin_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In")
            }

            // Zoom Out
            FloatingActionButton(
                onClick = { if (zoom > 4) onZoomChange(zoom - 1) },
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                modifier = Modifier.size(48.dp).testTag("action_strip_zoomout_btn")
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
            }

            // Locate Me
            FloatingActionButton(
                onClick = {
                    val uLat = userLat
                    val uLon = userLon
                    if (uLat != null && uLon != null) {
                        onMapPan(uLat - mapLat, uLon - mapLon)
                    } else {
                        onMapPan(48.8566 - mapLat, 2.3522 - mapLon)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                modifier = Modifier.size(48.dp).testTag("action_strip_locate_btn")
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Locate Me")
            }
        }

        // Rotation slider overlay at the bottom-right/center
        if (showRotationSlider) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = bottomPadding + 20.dp)
                    .width(220.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Rotate Map", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("${bearing.toInt()}°", style = MaterialTheme.typography.bodySmall)
                    }
                    Slider(
                        value = bearing,
                        onValueChange = onBearingChange,
                        valueRange = 0f..360f,
                        modifier = Modifier.testTag("rotation_slider")
                    )
                }
            }
        }

        // Floating Action Button Menu (Speed Dial) at the bottom-left
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = bottomPadding + 20.dp)
        ) {
            if (isFabMenuExpanded) {
                // Action 1: Toggle Stations
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SmallFloatingActionButton(
                        onClick = onToggleStationsBottomSheet,
                        modifier = Modifier.testTag("fab_toggle_stations"),
                        containerColor = if (showStationsBottomSheet) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ) {
                        Icon(
                            imageVector = if (showStationsBottomSheet) Icons.Default.LocalGasStation else Icons.Default.LocationOff,
                            contentDescription = "Toggle Stations"
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        )
                    ) {
                        Text(
                            text = if (showStationsBottomSheet) "Hide Stations" else "Show Stations",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Action 2: Toggle Rotation
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SmallFloatingActionButton(
                        onClick = onToggleRotationSlider,
                        modifier = Modifier.testTag("fab_toggle_rotation"),
                        containerColor = if (showRotationSlider) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ) {
                        Icon(
                            imageVector = if (showRotationSlider) Icons.Default.ScreenRotation else Icons.Default.CropRotate,
                            contentDescription = "Toggle Rotation"
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        )
                    ) {
                        Text(
                            text = if (showRotationSlider) "Hide Rotation" else "Show Rotation",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Main Fab Button
            FloatingActionButton(
                onClick = onToggleFabMenuExpanded,
                modifier = Modifier.testTag("fab_main_trigger"),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = if (isFabMenuExpanded) Icons.Default.Close else Icons.Default.MenuOpen,
                    contentDescription = "Debug Actions"
                )
            }
        }
    }
}