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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
    UsedApi("Eco-Movement (OCPI)", "https://eco-movement.com", null),
    UsedApi("Fastned (OCPI)", "https://fastnedcharging.com", null),
    UsedApi("DKV Mobility (OCPI)", "https://www.dkv-mobility.com", null),
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
@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentScreen) {
                            SettingsScreenPage.Main -> "Settings"
                            SettingsScreenPage.TollData -> "Highway toll"
                            SettingsScreenPage.ErrorLog -> "Error log"
                            SettingsScreenPage.About -> "About"
                            SettingsScreenPage.GoogleAccount -> "Google account"
                            SettingsScreenPage.VehicleConfig -> "Vehicle"
                            SettingsScreenPage.MapConfig -> "Map"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (screenStack.size > 1) {
                                screenStack = screenStack.dropLast(1)
                            } else {
                                onDismiss()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
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
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Map Engine
        Column {
            Text(
                "Map engine",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MapEngine.entries.forEach { engine ->
                    FilterChip(
                        selected = settings.phoneMapEngine == engine,
                        onClick = { onUpdate(settings.copy(phoneMapEngine = engine)) },
                        label = { Text(engine.name) },
                    )
                }
            }
        }

        if (settings.phoneMapEngine == MapEngine.MapLibre) {
            // Map Theme (for MapLibre)
            Column {
                Text(
                    "Map theme",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MapTheme.entries.forEach { theme ->
                        FilterChip(
                            selected = settings.mapTheme == theme,
                            onClick = { onUpdate(settings.copy(mapTheme = theme)) },
                            label = { Text(theme.name) },
                        )
                    }
                }
            }
        }

        // Data Sources
        Column {
            Text(
                "Data sources",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                "Electric",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    PoiProviderType.DataGouvElec to "data.gouv (France official)",
                    PoiProviderType.Chargy to "Chargy (Luxembourg)",
                    PoiProviderType.OpenChargeMap to "OpenChargeMap",
                    PoiProviderType.Fastned to "Fastned (OCPI)",
                    PoiProviderType.Dkv to "DKV Mobility (OCPI)",
                    PoiProviderType.EcoMovement to "Eco-Movement (OCPI)"
                ).forEach { (type, label) ->
                    FilterChip(
                        selected = settings.selectedPoiProviders.contains(type),
                        onClick = {
                            val next = if (settings.selectedPoiProviders.contains(type)) settings.selectedPoiProviders - type else settings.selectedPoiProviders + type
                            onUpdate(settings.copy(selectedPoiProviders = next))
                        },
                        label = { Text(label) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Column {
                Text(
                    "API keys (optional)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = settings.openChargeMapKey,
                    onValueChange = { onUpdate(settings.copy(openChargeMapKey = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("OpenChargeMap API key") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = settings.ecoMovementKey,
                    onValueChange = { onUpdate(settings.copy(ecoMovementKey = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Eco-Movement API key") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                ApiKeyHelpLink(
                    helpText = "Eco-Movement key is used as: Authorization: Token <key>.",
                    url = "https://developers.eco-movement.com",
                    linkLabel = "Eco-Movement docs"
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Fuel",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    PoiProviderType.Routex to "Routex",
                    PoiProviderType.Etalab to "Prix carburant (France official)",
                    PoiProviderType.GasApi to "gas-api.ovh",
                    PoiProviderType.DataGouv to "data.gouv (France official)",
                    PoiProviderType.UkCma to "UK Fuel Finder (CMA)",
                    PoiProviderType.ItalyMimit to "MIMIT (Italy official)",
                    PoiProviderType.SloveniaGorivaSi to "goriva.si (Slovenia official)",
                    PoiProviderType.NorwayDrivstoffAppen to "DrivstoffAppen (Norway)",
                    PoiProviderType.PortugalDgeg to "DGEG (Portugal official)",
                    PoiProviderType.NetherlandsAnwb to "ANWB (Netherlands/BE/LU)",
                    PoiProviderType.DenmarkFuelpricesDk to "Fuelprices.dk (Denmark)",
                    PoiProviderType.Fuelo to "Fuelo.net (multi-country)",
                    PoiProviderType.AustraliaNswFuelCheck to "FuelCheck (NSW Australia)",
                    PoiProviderType.CroatiaMzoe to "MZOE (Croatia official)",
                    PoiProviderType.FinlandPolttoaine to "Polttoaine.net (Finland)",
                    PoiProviderType.GreeceFuelGr to "FuelGR (Greece)",
                    PoiProviderType.IrelandPickAPump to "Pick A Pump (Ireland)",
                    PoiProviderType.MoldovaAnre to "ANRE (Moldova)",
                    PoiProviderType.RomaniaPeco to "Peco Online (Romania)",
                    PoiProviderType.SerbiaNis to "NIS (Serbia)",
                    PoiProviderType.MexicoCre to "CRE (Mexico)",
                    PoiProviderType.ArgentinaEnergia to "Secretaría de Energía (Argentina)",
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
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Column {
                Text(
                    "Fuel API keys (optional)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = settings.fuelpricesDkKey,
                    onValueChange = { onUpdate(settings.copy(fuelpricesDkKey = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Fuelprices.dk API key") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = settings.nswFuelCheckKey,
                    onValueChange = { onUpdate(settings.copy(nswFuelCheckKey = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("NSW FuelCheck API key") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = settings.nswFuelCheckSecret,
                    onValueChange = { onUpdate(settings.copy(nswFuelCheckSecret = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("NSW FuelCheck API secret") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // Traffic
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Show traffic", style = MaterialTheme.typography.titleSmall)
                Text("Google traffic layer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = settings.mapTrafficEnabled,
                onCheckedChange = { onUpdate(settings.copy(mapTrafficEnabled = it)) },
            )
        }

        // Debug Logging
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Debug logging", style = MaterialTheme.typography.titleSmall)
                Text("Capture network logs on map", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = settings.debugLoggingEnabled,
                onCheckedChange = { onUpdate(settings.copy(debugLoggingEnabled = it)) },
            )
        }

        // Map Filters
        Column {
            Text(
                "Map filters",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                "Fuel types",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MAP_ENERGY_OPTIONS.filter { it.first != "electric" }.forEach { (id, label) ->
                    FilterChip(
                        selected = settings.selectedMapEnergyTypes.contains(id),
                        onClick = {
                            val next = if (settings.selectedMapEnergyTypes.contains(id)) settings.selectedMapEnergyTypes - id else settings.selectedMapEnergyTypes + id
                            onUpdate(settings.copy(selectedMapEnergyTypes = next, useVehicleFilter = false))
                        },
                        label = { Text(label) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Power levels",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MAP_IRVE_POWER_OPTIONS.forEach { (kw, label) ->
                    FilterChip(
                        selected = settings.mapPowerLevels.contains(kw),
                        onClick = {
                            val next = if (settings.mapPowerLevels.contains(kw)) settings.mapPowerLevels - kw else settings.mapPowerLevels + kw
                            onUpdate(settings.copy(mapPowerLevels = next, useVehicleFilter = false))
                        },
                        label = { Text(label) },
                    )
                }
            }
        }

        // Itinerary
        Column {
            Text(
                "Itinerary",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                "Search radius: ${settings.routeStationSearchRadiusMeters} m",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = settings.routeStationSearchRadiusMeters.toFloat(),
                onValueChange = { onUpdate(settings.copy(routeStationSearchRadiusMeters = it.toInt())) },
                valueRange = 0f..2000f,
                steps = 19,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Only highway stations", style = MaterialTheme.typography.titleSmall)
                    Text("Filter results to stations on highways", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = settings.filterOnlyHighwayStations,
                    onCheckedChange = { onUpdate(settings.copy(filterOnlyHighwayStations = it)) },
                )
            }
        }
    }
}

private fun save(settingsManager: SettingsManager, s: AppSettings) {
    settingsManager.saveSettingsWithThemeCheck(s)
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

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                ListItem(
                    headlineContent = {
                        Text(
                            text = if (settings.isLoggedIn) "Hello, ${settings.googleUserName}" else "Not signed in",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    supportingContent = {
                        Text(
                            text = if (settings.isLoggedIn) "Google account connected" else "Sign in to sync your profile",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        when {
                            authManager == null -> Text("Auth unavailable", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            settings.isLoggedIn -> {
                                TextButton(
                                    onClick = {
                                        authManager.signOut { success ->
                                            if (!success) {
                                                scope.launch { snackbarHostState.showSnackbar("Sign out failed") }
                                            }
                                        }
                                    }
                                ) { Text("Sign out") }
                            }
                            else -> {
                                Button(
                                    onClick = {
                                        authManager.signIn(context) { success, error ->
                                            if (!success) {
                                                scope.launch { snackbarHostState.showSnackbar(error ?: "Sign in failed") }
                                            }
                                        }
                                    }
                                ) { Text("Sign in") }
                            }
                        }
                    }
                )
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
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
                    label = "Google account",
                    value = settings.googleUserName ?: "Not connected",
                    onClick = { onNavigate(SettingsScreenPage.GoogleAccount) }
                )
                SettingsItem(
                    label = "Highway toll",
                    value = if (!settings.tollDataPath.isNullOrBlank()) "Downloaded" else "Not downloaded",
                    onClick = { onNavigate(SettingsScreenPage.TollData) }
                )
                SettingsItem(
                    label = "Error log",
                    value = "View recent errors",
                    onClick = { onNavigate(SettingsScreenPage.ErrorLog) }
                )
                SettingsItem(
                    label = "About",
                    value = "Version & build info",
                    onClick = { onNavigate(SettingsScreenPage.About) }
                )
                SettingsItem(
                    label = "Clear cache",
                    value = "Markers, images, logs & temp files",
                    onClick = { showClearCacheConfirm = true }
                )
            }

            Spacer(Modifier.height(12.dp))
        }
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
            .padding(20.dp)
    ) {
        Text(
            text = "French highway toll estimation uses OpenTollData. Download the data file to see estimated tolls on planned routes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            text = if (downloaded) "Status: Downloaded" else "Status: Not downloaded",
            color = if (downloaded) Color(0xFF7FFF7F) else Color(0xFFFFB366),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        fileInfo?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        Text(
            text = "Path: $displayPath",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    ) {
                        Text("Download toll data (OpenTollData)")
                    }
                }

                else -> {
                    val (bytes, total) = progress
                    val pct = if (total != null && total > 0) (100 * bytes / total).toInt() else null
                    Text(
                        text = if (pct != null) "Downloading… $pct%" else "Downloading… ${bytes / (1024 * 1024)} MB",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        downloadError?.let { err ->
            Text(
                text = "Error: $err",
                color = Color(0xFFFF6B6B),
                style = MaterialTheme.typography.bodyMedium,
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
            .padding(20.dp)
    ) {
        Text(
            text = "Gaston",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(24.dp))
        AboutRow("Version name", BuildConfig.VERSION_NAME)
        AboutRow("Version code", BuildConfig.VERSION_CODE.toString())
        AboutRow("Build date", BuildConfig.BUILD_DATE)
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Used APIs & services",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = Uri.parse(url).host ?: url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (attribution != null) {
                Text(
                    text = attribution,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = "Open website",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SettingsItem(
    label: String,
    value: String? = null,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(label, style = MaterialTheme.typography.titleSmall) },
        supportingContent = {
            if (value != null) {
                Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        trailingContent = {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
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
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            extra?.invoke()
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 8.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
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
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (authManager == null) {
            Text("Authentication is currently unavailable.", color = MaterialTheme.colorScheme.onSurface)
        } else {
            val firebaseUser = remember { firebaseAuth?.currentUser }
            if (settings.googleUserName != null || firebaseUser != null) {
                Text(
                    "Connected as ${settings.googleUserName ?: firebaseUser?.displayName ?: "User"}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Logout")
                }
            } else {
                Text(
                    "Sign in to personalize your experience.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            .padding(20.dp)
    ) {
        ConfigTextField("Brand", settings.vehicleBrand) { onUpdate(settings.copy(vehicleBrand = it)) }
        ConfigTextField("Model", settings.vehicleModel) { onUpdate(settings.copy(vehicleModel = it)) }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Energy type",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FilterChip(
                selected = settings.vehicleEnergy == "gas",
                onClick = { onUpdate(settings.copy(vehicleEnergy = "gas")) },
                label = { Text("Gas") },
            )
            FilterChip(
                selected = settings.vehicleEnergy == "electric",
                onClick = { onUpdate(settings.copy(vehicleEnergy = "electric")) },
                label = { Text("Electric") },
            )
            FilterChip(
                selected = settings.vehicleEnergy == "hybrid",
                onClick = { onUpdate(settings.copy(vehicleEnergy = "hybrid")) },
                label = { Text("Hybrid") },
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        if (settings.vehicleEnergy == "gas" || settings.vehicleEnergy == "hybrid") {
            Text(
                "Preferred gas types",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
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
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Fuel card",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
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
            Text(
                "Preferred power range",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
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
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
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
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface),
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
                .padding(20.dp)
        ) {
            if (reversedLog.isEmpty()) {
                Text("No errors recorded", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "[$timestamp] $httpCode",
                                style = MaterialTheme.typography.labelMedium,
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
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = error.message, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
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
