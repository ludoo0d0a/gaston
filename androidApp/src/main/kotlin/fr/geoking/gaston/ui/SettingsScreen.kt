package fr.geoking.gaston.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import fr.geoking.gaston.AppSettings
import fr.geoking.gaston.FuelCard
import fr.geoking.gaston.MapEngine
import fr.geoking.gaston.MapTheme
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.feature.auth.GoogleAuthManager
import fr.geoking.gaston.poi.PoiProviderType
import fr.geoking.gaston.poi.anyProvidesElectric
import fr.geoking.gaston.poi.isUserSelectablePoiDataSource
import fr.geoking.gaston.CacheManager
import fr.geoking.gaston.BuildConfig
import fr.geoking.gaston.shared.diagnostics.DetailedError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SettingsScreenPage {
    Main,
    GoogleAccount,
    TollData,
    ErrorLog,
    VehicleConfig,
    MapConfig,
    About
}

private val Lavender = Color(0xFFD1D5FF)
private val DeepPurple = Color(0xFF21004C)
private val DarkBackground = Color(0xFF0A0A0A)
private val SeparatorColor = Color(0xFF2D2D44)

/** Used in About screen: API/service name, website URL, optional logo URL, optional license/credit line. */
private data class UsedApi(
    val name: String,
    val url: String,
    val logoUrl: String? = null,
    val attribution: String? = null
)

private val UsedApisList = listOf(
    // Routing & maps
    UsedApi("OSRM", "https://project-osrm.org", "https://project-osrm.org/favicon.ico"),
    UsedApi("Overpass API (OpenStreetMap)", "https://wiki.openstreetmap.org/wiki/Overpass_API", "https://www.openstreetmap.org/favicon.ico"),
    // POI & fuel / charging
    UsedApi("Open Charge Map", "https://openchargemap.org", "https://openchargemap.org/favicon.ico"),
    UsedApi("data.gouv.fr", "https://www.data.gouv.fr", "https://www.data.gouv.fr/favicon.ico"),
    UsedApi("ODRE (bornes IRVE)", "https://odre.opendatasoft.com", null),
    UsedApi("Gas API (prix carburants)", "https://gas-api.ovh", null),
    UsedApi(
        name = "OpenVan.camp",
        url = "https://openvan.camp",
        logoUrl = null,
        attribution = "Weekly fuel price reference data (Luxembourg and others). Licensed under CC BY 4.0; attribution to OpenVan.camp required."
    ),
    UsedApi("data.economie.gouv.fr", "https://data.economie.gouv.fr", null),
    UsedApi("Routex / Wigeogis", "https://www.wigeogis.com", null),
    UsedApi("Belib (Paris EV)", "https://opendata.paris.fr", null),
    UsedApi("Hérault Data (camping-car)", "https://www.herault-data.fr", null),
    // Traffic & toll
    UsedApi("CITA (trafic Luxembourg)", "https://www.cita.lu", "https://www.cita.lu/favicon.ico"),
    UsedApi("OpenTollData", "https://github.com/louis2038/OpenTollData", null),
)

@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    authManager: GoogleAuthManager?,
    errorLog: List<DetailedError>,
    onDismiss: () -> Unit,
    initialScreenStack: List<SettingsScreenPage>? = null,
    onInitialRouteConsumed: () -> Unit = {}
) {
    val current by settingsManager.settings.collectAsState()
    var screenStack by remember { mutableStateOf(listOf(SettingsScreenPage.Main)) }
    val currentScreen = screenStack.last()

    LaunchedEffect(initialScreenStack) {
        val stack = initialScreenStack
        if (stack != null && stack.isNotEmpty()) {
            screenStack = stack
            onInitialRouteConsumed()
        }
    }

    BackHandler {
        if (screenStack.size > 1) {
            screenStack = screenStack.dropLast(1)
        } else {
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(DeepPurple, DarkBackground)
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsHeader(
                title = when (currentScreen) {
                    SettingsScreenPage.Main -> "Gaston Settings"
                    SettingsScreenPage.TollData -> "Highway toll (OpenTollData)"
                    SettingsScreenPage.ErrorLog -> "Error Log"
                    SettingsScreenPage.About -> "About"
                    SettingsScreenPage.GoogleAccount -> "Google Account"
                    SettingsScreenPage.VehicleConfig -> "Vehicle"
                    SettingsScreenPage.MapConfig -> "Map Settings"
                },
                onBack = {
                    if (screenStack.size > 1) {
                        screenStack = screenStack.dropLast(1)
                    } else {
                        onDismiss()
                    }
                }
            )

            Box(modifier = Modifier.weight(1f)) {
                when (currentScreen) {
                    SettingsScreenPage.Main -> MainMenu(
                        settings = current,
                        authManager = authManager,
                        onNavigate = { screenStack = screenStack + it }
                    )
                    SettingsScreenPage.VehicleConfig -> VehicleConfig(
                        settings = current,
                        onUpdate = { save(settingsManager, it) }
                    )
                    SettingsScreenPage.TollData -> TollDataSection(
                        settings = current,
                        onUpdate = { save(settingsManager, it) }
                    )
                    SettingsScreenPage.ErrorLog -> ErrorLog(errorLog)
                    SettingsScreenPage.About -> AboutContent()
                    SettingsScreenPage.GoogleAccount -> GoogleAccount(
                        settings = current,
                        settingsManager = settingsManager,
                        authManager = authManager,
                        firebaseAuth = try { com.google.firebase.auth.FirebaseAuth.getInstance() } catch (e: Exception) { null }
                    )
                    SettingsScreenPage.MapConfig -> MapConfig(
                        settings = current,
                        onUpdate = { save(settingsManager, it) }
                    )
                }
            }
        }

    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapConfig(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Map Engine
        Column {
            Text("Map Engine", color = Lavender, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MapEngine.entries.forEach { engine ->
                    FilterChip(
                        selected = settings.phoneMapEngine == engine,
                        onClick = { onUpdate(settings.copy(phoneMapEngine = engine)) },
                        label = { Text(engine.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Lavender,
                            selectedLabelColor = DeepPurple,
                            labelColor = Color.White,
                            containerColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                }
            }
        }

        if (settings.phoneMapEngine == MapEngine.MapLibre) {
            // Map Theme (for MapLibre)
            Column {
                Text("Map Theme", color = Lavender, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MapTheme.entries.forEach { theme ->
                        FilterChip(
                            selected = settings.mapTheme == theme,
                            onClick = { onUpdate(settings.copy(mapTheme = theme)) },
                            label = { Text(theme.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Lavender,
                                selectedLabelColor = DeepPurple,
                                labelColor = Color.White,
                                containerColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                    }
                }
            }
        }

        // Data Sources
        Column {
            Text("Data Sources", color = Lavender, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))

            Text("Electric", color = Lavender.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    PoiProviderType.DataGouvElec to "data.gouv (France official)",
                    PoiProviderType.Chargy to "Chargy (Luxembourg)",
                    PoiProviderType.OpenChargeMap to "OpenChargeMap"
                ).forEach { (type, label) ->
                    FilterChip(
                        selected = settings.selectedPoiProviders.contains(type),
                        onClick = {
                            val next = if (settings.selectedPoiProviders.contains(type)) settings.selectedPoiProviders - type else settings.selectedPoiProviders + type
                            onUpdate(settings.copy(selectedPoiProviders = next))
                        },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Lavender,
                            selectedLabelColor = DeepPurple,
                            labelColor = Color.White,
                            containerColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Fuel", color = Lavender.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    PoiProviderType.Routex to "Routex",
                    PoiProviderType.Etalab to "Prix carburant (France official)",
                    PoiProviderType.GasApi to "gas-api.ovh",
                    PoiProviderType.DataGouv to "data.gouv (France official)",
                    PoiProviderType.OpenVanCamp to "OpenVan.camp (LU, HR, SI...)",
                    PoiProviderType.SpainMinetur to "Spain Minetur (official)",
                    PoiProviderType.GermanyTankerkoenig to "Tankerkönig (Germany)",
                    PoiProviderType.AustriaEControl to "E-Control (Austria)",
                    PoiProviderType.BelgiumOfficial to "Belgium (official)"
                ).filter { (type, _) -> type.isUserSelectablePoiDataSource() }.forEach { (type, label) ->
                    FilterChip(
                        selected = settings.selectedPoiProviders.contains(type),
                        onClick = {
                            val next = if (settings.selectedPoiProviders.contains(type)) settings.selectedPoiProviders - type else settings.selectedPoiProviders + type
                            onUpdate(settings.copy(selectedPoiProviders = next))
                        },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Lavender,
                            selectedLabelColor = DeepPurple,
                            labelColor = Color.White,
                            containerColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                }
            }
        }

        // Traffic
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Show Traffic", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text("Google traffic layer", color = Lavender.copy(alpha = 0.7f), fontSize = 14.sp)
            }
            Switch(
                checked = settings.mapTrafficEnabled,
                onCheckedChange = { onUpdate(settings.copy(mapTrafficEnabled = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Lavender,
                    checkedTrackColor = DeepPurple,
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color.DarkGray
                )
            )
        }

        // Debug Logging
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Debug Logging", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text("Capture network logs on map", color = Lavender.copy(alpha = 0.7f), fontSize = 14.sp)
            }
            Switch(
                checked = settings.debugLoggingEnabled,
                onCheckedChange = { onUpdate(settings.copy(debugLoggingEnabled = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Lavender,
                    checkedTrackColor = DeepPurple,
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color.DarkGray
                )
            )
        }

        // Map Filters
        Column {
            Text("Map Filters", color = Lavender, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))

            Text("Fuel Types", color = Lavender.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MAP_ENERGY_OPTIONS.filter { it.first != "electric" }.forEach { (id, label) ->
                    FilterChip(
                        selected = settings.selectedMapEnergyTypes.contains(id),
                        onClick = {
                            val next = if (settings.selectedMapEnergyTypes.contains(id)) settings.selectedMapEnergyTypes - id else settings.selectedMapEnergyTypes + id
                            onUpdate(settings.copy(selectedMapEnergyTypes = next, useVehicleFilter = false))
                        },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Lavender,
                            selectedLabelColor = DeepPurple,
                            labelColor = Color.White,
                            containerColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Power Levels", color = Lavender.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MAP_IRVE_POWER_OPTIONS.forEach { (kw, label) ->
                    FilterChip(
                        selected = settings.mapPowerLevels.contains(kw),
                        onClick = {
                            val next = if (settings.mapPowerLevels.contains(kw)) settings.mapPowerLevels - kw else settings.mapPowerLevels + kw
                            onUpdate(settings.copy(mapPowerLevels = next, useVehicleFilter = false))
                        },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Lavender,
                            selectedLabelColor = DeepPurple,
                            labelColor = Color.White,
                            containerColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                }
            }
        }

        // Itinerary
        Column {
            Text("Itinerary", color = Lavender, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))

            Text("Search radius: ${settings.routeStationSearchRadiusMeters}m", color = Lavender.copy(alpha = 0.7f), fontSize = 14.sp)
            Slider(
                value = settings.routeStationSearchRadiusMeters.toFloat(),
                onValueChange = { onUpdate(settings.copy(routeStationSearchRadiusMeters = it.toInt())) },
                valueRange = 0f..2000f,
                steps = 19,
                colors = SliderDefaults.colors(
                    thumbColor = Lavender,
                    activeTrackColor = Lavender,
                    inactiveTrackColor = Color.DarkGray
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Only Highway Stations", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text("Filter results to stations on highways", color = Lavender.copy(alpha = 0.7f), fontSize = 12.sp)
                }
                Switch(
                    checked = settings.filterOnlyHighwayStations,
                    onCheckedChange = { onUpdate(settings.copy(filterOnlyHighwayStations = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Lavender,
                        checkedTrackColor = DeepPurple,
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    )
                )
            }
        }
    }
}

private fun save(settingsManager: SettingsManager, s: AppSettings) {
    settingsManager.saveSettingsWithThemeCheck(s)
}

@Composable
private fun SettingsHeader(title: String, onBack: () -> Unit) {
    Surface(
        color = Color(0xFF1A1A2E),
        shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Lavender,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MainMenu(
    settings: AppSettings,
    authManager: GoogleAuthManager?,
    onNavigate: (SettingsScreenPage) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showClearCacheConfirm by remember { mutableStateOf(false) }

    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text("Clear Cache") },
            text = { Text("This will clear map markers, image caches, and debug logs. Continue?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheConfirm = false
                        scope.launch {
                            CacheManager.clearAllCaches(context)
                            snackbarHostState.showSnackbar("Cache cleared")
                        }
                    }
                ) {
                    Text("Clear", color = Color(0xFFFF6B6B))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp)
        ) {
            // Google Auth Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (settings.isLoggedIn) "Hello, ${settings.googleUserName}" else "Not signed in",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (settings.isLoggedIn) "Google Account connected" else "Sign in to sync your profile",
                        color = Lavender.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }
                if (authManager != null) {
                    if (settings.isLoggedIn) {
                        TextButton(onClick = {
                            authManager.signOut { success ->
                                if (!success) {
                                    scope.launch { snackbarHostState.showSnackbar("Sign out failed") }
                                }
                            }
                        }) {
                            Text("Sign Out", color = Color(0xFFFF6B6B))
                        }
                    } else {
                        Button(
                            onClick = {
                                authManager.signIn(context) { success, error ->
                                    if (!success) {
                                        scope.launch { snackbarHostState.showSnackbar(error ?: "Sign in failed") }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = DeepPurple)
                        ) {
                            Text("Sign In")
                        }
                    }
                } else {
                    Text("Auth Unavailable", color = Color.Gray, fontSize = 14.sp)
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                thickness = 0.5.dp,
                color = SeparatorColor
            )

        SettingsItem(
            label = "Vehicle",
            value = if (settings.vehicleBrand.isNotEmpty()) "${settings.vehicleBrand} ${settings.vehicleModel}" else "Not configured",
            onClick = { onNavigate(SettingsScreenPage.VehicleConfig) }
        )
        SettingsItem(
            label = "Map",
            value = "Data sources, traffic, filters",
            onClick = { onNavigate(SettingsScreenPage.MapConfig) }
        )

        SettingsItem(
            label = "Google Account",
            value = settings.googleUserName ?: "Not connected",
            onClick = { onNavigate(SettingsScreenPage.GoogleAccount) }
        )

        SettingsItem(
            label = "Highway toll (OpenTollData)",
            value = if (!settings.tollDataPath.isNullOrBlank()) "Downloaded" else "Not downloaded",
            onClick = { onNavigate(SettingsScreenPage.TollData) }
        )
        SettingsItem(
            label = "Error Log",
            value = "View recent errors",
            onClick = { onNavigate(SettingsScreenPage.ErrorLog) }
        )
        SettingsItem(
            label = "About",
            value = "Version & build info",
            onClick = { onNavigate(SettingsScreenPage.About) }
        )
        SettingsItem(
            label = "Clear Cache",
            value = "Markers, images, logs & temp files",
            onClick = { showClearCacheConfirm = true }
        )
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter)
    )
    }
}

@Composable
private fun TollDataSection(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit
) {
    val context = LocalContext.current
    val helper = remember(context) { OpenTollDataHelper(context) }
    val scope = rememberCoroutineScope()
    var downloadProgress by remember { mutableStateOf<Pair<Long, Long?>?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    val downloaded = helper.isTollDataDownloaded(settings)
    val displayPath = helper.getDisplayPath(settings)

    val fileInfo = remember(settings.tollDataPath) {
        settings.tollDataPath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                val size = file.length()
                val lastModified = file.lastModified()
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastModified))
                val sizeStr = if (size > 1024 * 1024) "${size / (1024 * 1024)} MB" else "${size / 1024} KB"
                "Size: $sizeStr, Downloaded: $dateStr"
            } else null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "French highway toll estimation uses OpenTollData. Download the data file to see estimated tolls on planned routes.",
            color = Lavender,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            text = if (downloaded) "Status: Downloaded" else "Status: Not downloaded",
            color = if (downloaded) Color(0xFF7FFF7F) else Color(0xFFFFB366),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        fileInfo?.let {
            Text(
                text = it,
                color = Lavender.copy(alpha = 0.8f),
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        Text(
            text = "Path: $displayPath",
            color = Lavender,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        if (!downloaded || downloadProgress != null) {
            when (val progress = downloadProgress) {
                null -> {
                    Button(
                        onClick = {
                            downloadError = null
                            downloadProgress = 0L to null
                            scope.launch {
                                val result = helper.download { bytes, total ->
                                    scope.launch(Dispatchers.Main) {
                                        downloadProgress = bytes to total
                                    }
                                }
                                withContext(Dispatchers.Main) { downloadProgress = null }
                                result.fold(
                                    onSuccess = { path ->
                                        withContext(Dispatchers.Main) {
                                            onUpdate(
                                                settings.copy(
                                                    tollDataPath = path
                                                )
                                            )
                                        }
                                    },
                                    onFailure = { e ->
                                        withContext(Dispatchers.Main) {
                                            downloadError = e.message ?: "Download failed"
                                        }
                                    }
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Lavender,
                            contentColor = DeepPurple
                        )
                    ) {
                        Text("Download toll data (OpenTollData)")
                    }
                }

                else -> {
                    val (bytes, total) = progress
                    val pct = if (total != null && total > 0) (100 * bytes / total).toInt() else null
                    Text(
                        text = if (pct != null) "Downloading… $pct%" else "Downloading… ${bytes / (1024 * 1024)} MB",
                        color = Lavender,
                        fontSize = 14.sp
                    )
                }
            }
        }
        downloadError?.let { err ->
            Text(
                text = "Error: $err",
                color = Color(0xFFFF6B6B),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun AboutContent() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Gaston",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(24.dp))
        AboutRow("Version name", BuildConfig.VERSION_NAME)
        AboutRow("Version code", BuildConfig.VERSION_CODE.toString())
        AboutRow("Build date", BuildConfig.BUILD_DATE)
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Used APIs & services",
            color = Lavender.copy(alpha = 0.9f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(12.dp))
        UsedApisList.forEach { api ->
            AboutApiRow(
                name = api.name,
                url = api.url,
                logoUrl = api.logoUrl,
                attribution = api.attribution,
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(api.url))
                    context.startActivity(intent)
                }
            )
        }
    }
}

@Composable
private fun AboutApiRow(
    name: String,
    url: String,
    logoUrl: String?,
    attribution: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(DeepPurple, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (logoUrl != null) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Text(
                    text = name.first().uppercaseChar().toString(),
                    color = Lavender,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = Uri.parse(url).host ?: url,
                color = Lavender.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
            if (attribution != null) {
                Text(
                    text = attribution,
                    color = Lavender.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = "Open website",
            tint = Lavender.copy(alpha = 0.8f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Lavender.copy(alpha = 0.8f),
            fontSize = 16.sp
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SettingsItem(
    label: String,
    value: String? = null,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 22.sp, // Bigger font
                    fontWeight = FontWeight.Medium
                )
                if (value != null) {
                    Text(
                        text = value,
                        color = Lavender.copy(alpha = 0.7f),
                        fontSize = 16.sp // Bigger font
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Lavender,
                modifier = Modifier.size(24.dp)
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp),
            thickness = 0.5.dp,
            color = SeparatorColor
        )
    }
}

@Composable
private fun SelectionItem(
    label: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    extra: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = if (isSelected) Lavender else Color.White,
                fontSize = 20.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            extra?.invoke()
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Lavender,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp),
            thickness = 0.5.dp,
            color = SeparatorColor
        )
    }
}

@Composable
private fun GoogleAccount(
    settings: AppSettings,
    settingsManager: SettingsManager,
    authManager: GoogleAuthManager?,
    firebaseAuth: com.google.firebase.auth.FirebaseAuth?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (authManager == null) {
            Text("Authentication is currently unavailable.", color = Color.White)
        } else {
            val firebaseUser = remember { firebaseAuth?.currentUser }
            if (settings.googleUserName != null || firebaseUser != null) {
                Text(
                    "Connected as ${settings.googleUserName ?: firebaseUser?.displayName ?: "User"}",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        authManager.signOut { success ->
                            if (!success) {
                                android.util.Log.e("GoogleAuth", "Sign-out failed")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.7f), contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Logout")
                }
            } else {
                Text(
                    "Sign in to personalize your experience.",
                    color = Lavender,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                Button(
                    onClick = {
                        scope.launch {
                            authManager.signIn(context) { success, error ->
                                if (!success) {
                                    android.util.Log.e("GoogleAuth", "Sign-in failed: ${error ?: "Unknown error"}")
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = DeepPurple),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sign in with Google")
                }
            }
        }
    }
}

@Composable
private fun VehicleConfig(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        ConfigTextField("Brand", settings.vehicleBrand) { onUpdate(settings.copy(vehicleBrand = it)) }
        ConfigTextField("Model", settings.vehicleModel) { onUpdate(settings.copy(vehicleModel = it)) }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Energy Type", color = Lavender, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FilterChip(
                selected = settings.vehicleEnergy == "gas",
                onClick = { onUpdate(settings.copy(vehicleEnergy = "gas")) },
                label = { Text("Gas") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Lavender,
                    selectedLabelColor = DeepPurple,
                    labelColor = Color.White,
                    containerColor = Color.White.copy(alpha = 0.1f)
                )
            )
            FilterChip(
                selected = settings.vehicleEnergy == "electric",
                onClick = { onUpdate(settings.copy(vehicleEnergy = "electric")) },
                label = { Text("Electric") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Lavender,
                    selectedLabelColor = DeepPurple,
                    labelColor = Color.White,
                    containerColor = Color.White.copy(alpha = 0.1f)
                )
            )
            FilterChip(
                selected = settings.vehicleEnergy == "hybrid",
                onClick = { onUpdate(settings.copy(vehicleEnergy = "hybrid")) },
                label = { Text("Hybrid") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Lavender,
                    selectedLabelColor = DeepPurple,
                    labelColor = Color.White,
                    containerColor = Color.White.copy(alpha = 0.1f)
                )
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        if (settings.vehicleEnergy == "gas" || settings.vehicleEnergy == "hybrid") {
            Text("Preferred Gas Types", color = Lavender, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MAP_ENERGY_OPTIONS.filter { it.first != "electric" }.forEach { (id, label) ->
                    FilterChip(
                        selected = settings.vehicleGasTypes.contains(id),
                        onClick = {
                            val newTypes = if (settings.vehicleGasTypes.contains(id)) settings.vehicleGasTypes - id else settings.vehicleGasTypes + id
                            onUpdate(settings.copy(vehicleGasTypes = newTypes))
                        },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Lavender,
                            selectedLabelColor = DeepPurple,
                            labelColor = Color.White,
                            containerColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Fuel Card", color = Lavender, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
            FuelCard.entries.forEach { card ->
                SelectionItem(
                    label = card.name,
                    isSelected = settings.fuelCard == card,
                    onSelect = { onUpdate(settings.copy(fuelCard = card)) }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (settings.vehicleEnergy == "electric" || settings.vehicleEnergy == "hybrid") {
            Text("Preferred Power Range", color = Lavender, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MAP_IRVE_POWER_OPTIONS.forEach { (id, label) ->
                    FilterChip(
                        selected = settings.vehiclePowerLevels.contains(id),
                        onClick = {
                            val newLevels = if (settings.vehiclePowerLevels.contains(id)) settings.vehiclePowerLevels - id else settings.vehiclePowerLevels + id
                            onUpdate(settings.copy(vehiclePowerLevels = newLevels))
                        },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Lavender,
                            selectedLabelColor = DeepPurple,
                            labelColor = Color.White,
                            containerColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ApiKeyHelpLink(
    helpText: String,
    url: String,
    linkLabel: String = "Create API key",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(modifier = modifier.padding(bottom = 4.dp)) {
        Text(
            text = helpText,
            color = Lavender.copy(alpha = 0.85f),
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = linkLabel,
                color = Lavender,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = Lavender.copy(alpha = 0.9f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ConfigTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = label,
            color = Lavender,
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 18.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Lavender,
                unfocusedBorderColor = SeparatorColor,
                focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                unfocusedContainerColor = Color.Black.copy(alpha = 0.2f)
            ),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun ErrorLog(errorLog: List<DetailedError>) {
    val scrollState = rememberScrollState()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val reversedLog = remember(errorLog) { errorLog.reversed() }

    SelectionContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp)
        ) {
            if (reversedLog.isEmpty()) {
                Text("No errors recorded", color = Lavender, fontSize = 18.sp)
            } else {
                Button(
                    onClick = {
                        val allErrors = reversedLog.joinToString("\n\n") { error ->
                            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(error.timestamp))
                            val httpCode = error.httpCode?.let { "HTTP $it" } ?: "Generic"
                            "[$timestamp] $httpCode\n${error.message}"
                        }
                        scope.launch {
                            clipboard.setClipEntry(androidx.compose.ui.platform.ClipEntry(android.content.ClipData.newPlainText("", allErrors)))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Lavender,
                        contentColor = DeepPurple
                    )
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy All Logs")
                }

                reversedLog.forEach { error ->
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(error.timestamp))
                    val httpCode = error.httpCode?.let { "HTTP $it" } ?: "Generic"

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "[$timestamp] $httpCode",
                                color = Lavender,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    val errorText = "[$timestamp] $httpCode\n${error.message}"
                                    scope.launch {
                                        clipboard.setClipEntry(androidx.compose.ui.platform.ClipEntry(android.content.ClipData.newPlainText("", errorText)))
                                    }
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy error",
                                    tint = Lavender,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = error.message, color = Color.White, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
fun SettingsScreenPreview() {
    val context = LocalContext.current
    val mockSettingsManager = remember(context) {
        object : SettingsManager(context) {
            private val mockSettings = MutableStateFlow(
                AppSettings(selectedPoiProviders = setOf(PoiProviderType.DataGouv))
            )
            override val settings: StateFlow<AppSettings> = mockSettings.asStateFlow()
            override fun saveSettings(settings: AppSettings) {
                mockSettings.value = settings
            }
        }
    }
    val diagnostics = remember { fr.geoking.gaston.shared.diagnostics.DiagnosticStore() }
    val mockAuthManager = GoogleAuthManager(
        context,
        mockSettingsManager,
        diagnostics,
        com.google.firebase.auth.FirebaseAuth.getInstance()
    )

    SettingsScreen(
        settingsManager = mockSettingsManager,
        authManager = mockAuthManager,
        errorLog = emptyList(),
        onDismiss = {}
    )
}
