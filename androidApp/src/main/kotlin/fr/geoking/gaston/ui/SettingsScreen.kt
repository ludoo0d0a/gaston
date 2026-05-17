package fr.geoking.gaston.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import fr.geoking.gaston.AppSettings
import fr.geoking.gaston.effectiveMapEnergyFilterIds
import fr.geoking.gaston.effectiveIrvePowerLevels
import fr.geoking.gaston.FuelCard
import fr.geoking.gaston.MapEngine
import fr.geoking.gaston.MapTheme
import fr.geoking.gaston.PoiProviderSelectionMode
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.R
import fr.geoking.gaston.ThemeMode
import fr.geoking.gaston.feature.auth.GoogleAuthManager
import fr.geoking.gaston.poi.PoiProviderType
import fr.geoking.gaston.poi.anyProvidesElectric
import fr.geoking.gaston.poi.isUserSelectablePoiDataSource
import fr.geoking.gaston.CacheManager
import fr.geoking.gaston.BuildConfig
import fr.geoking.gaston.poi.FuelPriceRegistry
import fr.geoking.gaston.shared.diagnostics.DetailedError
import fr.geoking.gaston.ui.components.DisclaimerDialog
import fr.geoking.gaston.ui.components.FuelFilterChip
import fr.geoking.gaston.ui.components.PowerFilterChip
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
    TollData,
    ErrorLog,
    VehicleConfig,
    MapConfig,
    Sources,
    App,
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
    var showDisclaimer by remember { mutableStateOf(false) }

    if (showDisclaimer) {
        DisclaimerDialog(onAccept = { showDisclaimer = false })
    }

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
                            SettingsScreenPage.VehicleConfig -> "Vehicle"
                            SettingsScreenPage.MapConfig -> "Map"
                            SettingsScreenPage.Sources -> "Sources"
                            SettingsScreenPage.App -> "App"
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
                SettingsScreenPage.About -> AboutContent(onShowDisclaimer = { showDisclaimer = true })
                SettingsScreenPage.MapConfig -> MapConfig(
                    settings = current,
                    onUpdate = { save(settingsManager, it) }
                )
                SettingsScreenPage.Sources -> SourcesConfig(
                    settings = current,
                    onUpdate = { save(settingsManager, it) }
                )
                SettingsScreenPage.App -> AppConfig(
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
            val selectedEnergyIds = settings.selectedMapEnergyTypes
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxLines = 2,
            ) {
                MAP_ENERGY_OPTIONS.filter { it.first != "electric" }.forEach { (id, label) ->
                    FuelFilterChip(
                        id = id,
                        label = label,
                        isSelected = selectedEnergyIds.contains(id),
                        onClick = {
                            onUpdate(settings.copy(selectedMapEnergyTypes = setOf(id), useVehicleFilter = false))
                        }
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
            val effectivePowerLevels = settings.effectiveIrvePowerLevels()
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxLines = 2,
            ) {
                MAP_IRVE_POWER_OPTIONS.forEach { (kw, label) ->
                    PowerFilterChip(
                        kw = kw,
                        label = label,
                        isSelected = effectivePowerLevels.contains(kw),
                        onClick = {
                            val next = setOf(kw)
                            onUpdate(settings.copy(mapPowerLevels = next, useVehicleFilter = false))
                        }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourcesConfig(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit
) {
    data class ProviderUiInfo(
        val type: PoiProviderType,
        val label: String,
        /** ISO country codes (e.g. "FR", "BE") or special values ("GLOBAL", "EU"). */
        val supportedCountries: List<String>,
        val providesFuel: Boolean = type.providesFuel,
        val providesElectric: Boolean = type.providesElectric
    )

    fun countryLabel(code: String): String {
        val c = code.uppercase()
        return when (c) {
            "GLOBAL" -> "Global"
            "EU" -> "Europe"
            "PT-AC" -> "Portugal (Azores)"
            "PT-MA" -> "Portugal (Madeira)"
            "ES-CN" -> "Spain (Canary Islands)"
            "ES-IB" -> "Spain (Balearic Islands)"
            else -> {
                if (c.length == 2) {
                    // Prefer device locale for display names.
                    Locale("", c).getDisplayCountry(Locale.getDefault()).ifBlank { c }
                } else {
                    c
                }
            }
        }
    }

    // Keep this list in sync with FueloProvider.getConfigForLocation()
    val fueloSupported = listOf(
        "BG", "CZ", "HU", "PL", "SK",
        "EE", "LV", "LT",
        "CH", "BA", "TR", "MK",
        "PT-AC", "PT-MA", "PT",
        "ES-CN", "ES-IB", "ES",
        "IE", "GB",
        "AT", "BE", "DE", "FR", "GR", "HR", "IT", "NL", "RO", "RS", "SI"
    )

    val providers = listOf(
        // Electric
        ProviderUiInfo(PoiProviderType.DataGouvElec, "data.gouv.fr (Electric)", listOf("FR")),
        ProviderUiInfo(PoiProviderType.Chargy, "Chargy", listOf("LU")),
        ProviderUiInfo(PoiProviderType.OpenChargeMap, "OpenChargeMap", listOf("GLOBAL")),
        ProviderUiInfo(PoiProviderType.Fastned, "Fastned (OCPI)", listOf("GB")),
        ProviderUiInfo(PoiProviderType.Dkv, "DKV Mobility (OCPI)", listOf("EU")),
        ProviderUiInfo(PoiProviderType.EcoMovement, "Eco‑Movement (OCPI)", listOf("GLOBAL")),
        ProviderUiInfo(PoiProviderType.Overpass, "OpenStreetMap (Overpass API)", listOf("GLOBAL")),

        // Fuel
        ProviderUiInfo(PoiProviderType.Routex, "Routex", listOf("EU")),
        ProviderUiInfo(PoiProviderType.Etalab, "data.gouv.fr (Fuel instant)", listOf("FR")),
        ProviderUiInfo(PoiProviderType.GasApi, "gas-api.ovh", listOf("FR")),
        ProviderUiInfo(PoiProviderType.DataGouv, "data.gouv.fr (Fuel daily)", listOf("FR")),
        ProviderUiInfo(PoiProviderType.UkCma, "UK Fuel Finder (CMA)", listOf("GB")),
        ProviderUiInfo(PoiProviderType.ItalyMimit, "MIMIT (Italy official)", listOf("IT")),
        ProviderUiInfo(PoiProviderType.SloveniaGorivaSi, "goriva.si (Slovenia official)", listOf("SI")),
        ProviderUiInfo(PoiProviderType.NorwayDrivstoffAppen, "DrivstoffAppen (Norway)", listOf("NO")),
        ProviderUiInfo(PoiProviderType.SwedenDrivstoffAppen, "DrivstoffAppen / bensinpriser.nu (Sweden)", listOf("SE")),
        ProviderUiInfo(PoiProviderType.PortugalDgeg, "DGEG (Portugal official)", listOf("PT")),
        ProviderUiInfo(PoiProviderType.NetherlandsAnwb, "ANWB", listOf("NL", "BE", "LU")),
        ProviderUiInfo(PoiProviderType.DenmarkFuelpricesDk, "Fuelprices.dk", listOf("DK")),
        ProviderUiInfo(PoiProviderType.Fuelo, "Fuelo.net", fueloSupported),
        ProviderUiInfo(PoiProviderType.AustraliaNswFuelCheck, "FuelCheck (NSW Australia)", listOf("AU")),
        ProviderUiInfo(PoiProviderType.CroatiaMzoe, "MZOE (Croatia official)", listOf("HR")),
        ProviderUiInfo(PoiProviderType.FinlandPolttoaine, "Polttoaine.net (Finland)", listOf("FI")),
        ProviderUiInfo(PoiProviderType.GreeceFuelGr, "FuelGR (Greece)", listOf("GR")),
        ProviderUiInfo(PoiProviderType.IrelandPickAPump, "Pick A Pump (Ireland)", listOf("IE")),
        ProviderUiInfo(PoiProviderType.MoldovaAnre, "ANRE (Moldova)", listOf("MD")),
        ProviderUiInfo(PoiProviderType.RomaniaPeco, "Peco Online (Romania)", listOf("RO")),
        ProviderUiInfo(PoiProviderType.SerbiaNis, "NIS (Serbia)", listOf("RS")),
        ProviderUiInfo(PoiProviderType.MexicoCre, "CRE (Mexico)", listOf("MX")),
        ProviderUiInfo(PoiProviderType.ArgentinaEnergia, "Secretaría de Energía (Argentina)", listOf("AR")),
        ProviderUiInfo(
            PoiProviderType.OpenVanCamp,
            "OpenVan.camp",
            FuelPriceRegistry.REFERENCE_PRICE_COUNTRIES.toList().sorted()
        ),
        ProviderUiInfo(PoiProviderType.SpainMinetur, "Spain Minetur (official)", listOf("ES")),
        ProviderUiInfo(PoiProviderType.GermanyTankerkoenig, "Tankerkönig (Germany)", listOf("DE")),
        ProviderUiInfo(PoiProviderType.AustriaEControl, "E‑Control (Austria)", listOf("AT")),
        ProviderUiInfo(PoiProviderType.BelgiumOfficial, "Belgium (official)", listOf("BE")),
    )
        .filter { it.type.isUserSelectablePoiDataSource() }
        .distinctBy { it.type }

    val providersByCountry: Map<String, List<ProviderUiInfo>> = remember(providers) {
        providers
            .flatMap { p -> p.supportedCountries.map { c -> c.uppercase() to p } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, list) -> list.distinctBy { it.type }.sortedBy { it.label.lowercase() } }
    }

    val sortedCountryKeys = remember(providersByCountry) {
        val keys = providersByCountry.keys
        buildList {
            if ("FR" in keys) add("FR")
            addAll(
                (keys - setOf("FR", "EU", "GLOBAL"))
                    .sortedBy { countryLabel(it).lowercase() }
            )
            if ("EU" in keys) add("EU")
            if ("GLOBAL" in keys) add("GLOBAL")
        }
    }

    var countryFilterText by remember { mutableStateOf("") }
    val filteredCountryKeys = remember(sortedCountryKeys, countryFilterText) {
        val q = countryFilterText.trim().lowercase()
        if (q.isEmpty()) sortedCountryKeys
        else sortedCountryKeys.filter { key ->
            countryLabel(key).lowercase().contains(q) ||
                key.lowercase().contains(q)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column {
            Text(
                "Selection mode",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(
                    selected = settings.poiProviderSelectionMode == PoiProviderSelectionMode.Manual,
                    onClick = { onUpdate(settings.copy(poiProviderSelectionMode = PoiProviderSelectionMode.Manual)) },
                    label = { Text("Manual") }
                )
                FilterChip(
                    selected = settings.poiProviderSelectionMode == PoiProviderSelectionMode.Auto,
                    onClick = { onUpdate(settings.copy(poiProviderSelectionMode = PoiProviderSelectionMode.Auto)) },
                    label = { Text("Auto (by country)") }
                )
            }
            if (settings.poiProviderSelectionMode == PoiProviderSelectionMode.Auto) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Auto selects sources based on your current country. Your manual selection below remains as a fallback.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column {
            Text(
                "Data sources",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = countryFilterText,
                onValueChange = { countryFilterText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Filter by country or region") },
                placeholder = { Text("France, DE, global…") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            if (filteredCountryKeys.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "No country matches this filter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            filteredCountryKeys.forEach { countryKey ->
                val list = providersByCountry[countryKey].orEmpty()
                if (list.isEmpty()) return@forEach

                val allTypesInCountry = list.map { it.type }.toSet()
                val selectedInCountry = allTypesInCountry.count { it in settings.selectedPoiProviders }
                val allOn = selectedInCountry == allTypesInCountry.size
                val chipLabelStyle = MaterialTheme.typography.labelSmall

                Column(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val next = if (allOn) {
                                    settings.selectedPoiProviders - allTypesInCountry
                                } else {
                                    settings.selectedPoiProviders + allTypesInCountry
                                }
                                onUpdate(settings.copy(selectedPoiProviders = next))
                            }
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                countryLabel(countryKey),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                when {
                                    allOn -> "${list.size} sources on — tap to turn all off"
                                    selectedInCountry == 0 -> "${list.size} sources off — tap to enable all"
                                    else -> "$selectedInCountry / ${list.size} on — tap to enable all"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (allOn) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                            )
                        }
                    }
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        list
                            .sortedWith(
                                compareBy<ProviderUiInfo> { !it.providesElectric }
                                    .thenBy { it.label.lowercase() }
                            )
                            .forEach { p ->
                                val isMultiCountry =
                                    p.supportedCountries.size > 1 ||
                                        p.supportedCountries.any { it == "GLOBAL" || it == "EU" }
                                FilterChip(
                                    selected = settings.selectedPoiProviders.contains(p.type),
                                    onClick = {
                                        val next =
                                            if (settings.selectedPoiProviders.contains(p.type)) {
                                                settings.selectedPoiProviders - p.type
                                            } else {
                                                settings.selectedPoiProviders + p.type
                                            }
                                        onUpdate(settings.copy(selectedPoiProviders = next))
                                    },
                                    label = {
                                        Text(
                                            p.label,
                                            maxLines = 1,
                                            style = chipLabelStyle,
                                        )
                                    },
                                    leadingIcon = if (isMultiCountry) {
                                        {
                                            Icon(
                                                Icons.Default.Public,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    modifier = Modifier.heightIn(min = 30.dp),
                                )
                            }
                    }
                }
            }

        }
    }
}

@Composable
private fun AppConfig(
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
        // Theme
        Column {
            Text(
                "App theme",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.uiThemeMode == mode,
                        onClick = { onUpdate(settings.copy(uiThemeMode = mode)) },
                        label = { Text(mode.name) },
                    )
                }
            }
            Text(
                "Also applies to maps for night driving",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

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
            if (settings.isPremium) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A8A)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color(0xFFFACC15),
                            shape = CircleShape,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black)
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Premium Active", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Thank you for supporting Gaston!", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
            }

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
                    value = "Traffic, filters",
                    onClick = { onNavigate(SettingsScreenPage.MapConfig) }
                )
                SettingsItem(
                    label = "Sources",
                    value = when (settings.poiProviderSelectionMode) {
                        PoiProviderSelectionMode.Auto -> "Auto (by country)"
                        PoiProviderSelectionMode.Manual -> "Manual selection"
                    },
                    onClick = { onNavigate(SettingsScreenPage.Sources) }
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
                    label = "App",
                    value = "API keys",
                    onClick = { onNavigate(SettingsScreenPage.App) }
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
private fun AboutContent(
    onShowDisclaimer: () -> Unit
) {
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
        Spacer(modifier = Modifier.height(16.dp))
        AboutRowClickable(
            label = stringResource(id = R.string.about_view_disclaimer),
            onClick = onShowDisclaimer
        )
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
private fun AboutRowClickable(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VehicleConfig(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit
) {
    var gasConsumptionText by remember { mutableStateOf(settings.gasConsumptionLper100km?.toString() ?: "") }
    var evConsumptionText by remember { mutableStateOf(settings.evConsumptionKwhPer100km?.toString() ?: "") }

    LaunchedEffect(settings.gasConsumptionLper100km) {
        val next = settings.gasConsumptionLper100km?.toString() ?: ""
        if (next != gasConsumptionText && next.replace(',', '.') != gasConsumptionText.replace(',', '.')) {
            gasConsumptionText = next
        }
    }

    LaunchedEffect(settings.evConsumptionKwhPer100km) {
        val next = settings.evConsumptionKwhPer100km?.toString() ?: ""
        if (next != evConsumptionText && next.replace(',', '.') != evConsumptionText.replace(',', '.')) {
            evConsumptionText = next
        }
    }

    fun handleConsumptionInput(input: String, onTextUpdate: (String) -> Unit, onValueUpdate: (Float?) -> Unit) {
        val normalized = input.replace(',', '.')
        if (normalized.isEmpty()) {
            onTextUpdate("")
            onValueUpdate(null)
            return
        }

        // Regex for 1-2 digits, optional dot/comma, optional 1 digit
        val regex = Regex("""^(\d{0,2})([.,]\d{0,1})?$""")
        if (regex.matches(input)) {
            onTextUpdate(input)
            val value = normalized.toFloatOrNull()
            if (value != null && value >= 1.0f && value <= 99.0f) {
                onValueUpdate(value)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Identity Section
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Identity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                ConfigTextField(
                    label = "Brand",
                    value = settings.vehicleBrand,
                    leadingIcon = Icons.Default.DirectionsCar
                ) { onUpdate(settings.copy(vehicleBrand = it)) }
                ConfigTextField(
                    label = "Model",
                    value = settings.vehicleModel,
                    leadingIcon = Icons.Default.Badge
                ) { onUpdate(settings.copy(vehicleModel = it)) }

                Text(
                    "Energy type",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "gas" to ("Gas" to Icons.Default.LocalGasStation),
                        "electric" to ("Electric" to Icons.Default.EvStation),
                        "hybrid" to ("Hybrid" to Icons.Default.SettingsInputComponent)
                    ).forEach { (id, info) ->
                        val (label, icon) = info
                        FilterChip(
                            selected = settings.vehicleEnergy == id,
                            onClick = { onUpdate(settings.copy(vehicleEnergy = id)) },
                            label = { Text(label) },
                            leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Specifications Section
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Specifications",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (settings.vehicleEnergy == "gas" || settings.vehicleEnergy == "hybrid") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ConfigTextField(
                            label = "Tank (L)",
                            value = settings.gasTankCapacityLiters?.toString() ?: "",
                            modifier = Modifier.weight(1f),
                            leadingIcon = Icons.Default.WaterDrop,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        ) { onUpdate(settings.copy(gasTankCapacityLiters = it.toFloatOrNull())) }

                        ConfigTextField(
                            label = "L/100km",
                            value = gasConsumptionText,
                            modifier = Modifier.weight(1f),
                            leadingIcon = Icons.Default.Speed,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        ) { input ->
                            handleConsumptionInput(
                                input = input,
                                onTextUpdate = { gasConsumptionText = it },
                                onValueUpdate = { onUpdate(settings.copy(gasConsumptionLper100km = it)) }
                            )
                        }
                    }
                }

                if (settings.vehicleEnergy == "electric" || settings.vehicleEnergy == "hybrid") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ConfigTextField(
                            label = "Battery (kWh)",
                            value = settings.batteryCapacityKwh?.toString() ?: "",
                            modifier = Modifier.weight(1f),
                            leadingIcon = Icons.Default.BatteryChargingFull,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        ) { onUpdate(settings.copy(batteryCapacityKwh = it.toFloatOrNull())) }

                        ConfigTextField(
                            label = "Range (km)",
                            value = settings.evRangeKm.toString(),
                            modifier = Modifier.weight(1f),
                            leadingIcon = Icons.Default.Map,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        ) { onUpdate(settings.copy(evRangeKm = it.toIntOrNull() ?: 300)) }
                    }
                    ConfigTextField(
                        label = "Consumption (kWh/100km)",
                        value = evConsumptionText,
                        leadingIcon = Icons.Default.Bolt,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    ) { input ->
                        handleConsumptionInput(
                            input = input,
                            onTextUpdate = { evConsumptionText = it },
                            onValueUpdate = { onUpdate(settings.copy(evConsumptionKwhPer100km = it)) }
                        )
                    }
                }
            }
        }

        // Preferences Section
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Preferences",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (settings.vehicleEnergy == "gas" || settings.vehicleEnergy == "hybrid") {
                    Text(
                        "Preferred gas types",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MAP_ENERGY_OPTIONS.filter { it.first != "electric" }.forEach { (id, label) ->
                            FuelFilterChip(
                                id = id,
                                label = label,
                                isSelected = settings.vehicleGasTypes.contains(id),
                                onClick = {
                                    val newTypes = if (settings.vehicleGasTypes.contains(id)) settings.vehicleGasTypes - id else settings.vehicleGasTypes + id
                                    onUpdate(settings.copy(vehicleGasTypes = newTypes))
                                }
                            )
                        }
                    }

                    Text(
                        "Fuel card",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    FuelCard.entries.forEach { card ->
                        SelectionItem(
                            label = card.name,
                            isSelected = settings.fuelCard == card,
                            onSelect = { onUpdate(settings.copy(fuelCard = card)) }
                        )
                    }
                }

                if (settings.vehicleEnergy == "electric" || settings.vehicleEnergy == "hybrid") {
                    Text(
                        "Preferred power range",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MAP_IRVE_POWER_OPTIONS.forEach { (id, label) ->
                            PowerFilterChip(
                                kw = id,
                                label = label,
                                isSelected = settings.vehiclePowerLevels.contains(id),
                                onClick = {
                                    val newLevels = if (settings.vehiclePowerLevels.contains(id)) settings.vehiclePowerLevels - id else settings.vehiclePowerLevels + id
                                    onUpdate(settings.copy(vehiclePowerLevels = newLevels))
                                }
                            )
                        }
                    }
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
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        leadingIcon = leadingIcon?.let {
            { Icon(it, contentDescription = null, modifier = Modifier.size(20.dp)) }
        },
        keyboardOptions = keyboardOptions,
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        textStyle = MaterialTheme.typography.bodyLarge
    )
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
                AppSettings(
                    selectedPoiProviders = setOf(PoiProviderType.DataGouv, PoiProviderType.Overpass),
                )
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
