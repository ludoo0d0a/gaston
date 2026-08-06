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
import androidx.annotation.StringRes
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
import fr.geoking.gaston.premium.BillingManager
import fr.geoking.gaston.premium.PremiumSubscriptionNotice
import org.koin.compose.koinInject
import java.text.DateFormat
import fr.geoking.gaston.BuildConfig
import fr.geoking.gaston.UsedApisList
import fr.geoking.gaston.poi.FuelPriceRegistry
import fr.geoking.gaston.shared.diagnostics.DetailedError
import fr.geoking.gaston.ui.components.DisclaimerDialog
import fr.geoking.gaston.ui.components.FuelFilterChip
import fr.geoking.gaston.ui.map.maplibre.MapLibreView
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.camera.CameraPosition
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
    Theme,
    About
}

@Composable
private fun MapEngine.displayLabel(): String = when (this) {
    MapEngine.Google -> stringResource(R.string.map_engine_google)
    MapEngine.MapLibre -> stringResource(R.string.map_engine_maplibre)
}

@Composable
private fun MapTheme.displayLabel(): String = when (this) {
    MapTheme.Dark -> stringResource(R.string.map_theme_dark_matter)
    MapTheme.Voyager -> stringResource(R.string.map_theme_voyager)
    MapTheme.Standard -> stringResource(R.string.map_theme_osm)
    MapTheme.Positron -> stringResource(R.string.map_theme_positron)
    MapTheme.Fiord -> stringResource(R.string.map_theme_fiord)
    MapTheme.OsmFr -> stringResource(R.string.map_theme_osm_fr)
    MapTheme.Hot -> stringResource(R.string.map_theme_hot)
    MapTheme.Bright -> stringResource(R.string.map_theme_bright)
    MapTheme.Liberty -> stringResource(R.string.map_theme_liberty)
}

@Composable
private fun MapTheme.displayDescription(): String = when (this) {
    MapTheme.Dark -> stringResource(R.string.map_theme_dark_matter_desc)
    MapTheme.Voyager -> stringResource(R.string.map_theme_voyager_desc)
    MapTheme.Standard -> stringResource(R.string.map_theme_osm_desc)
    MapTheme.Positron -> stringResource(R.string.map_theme_positron_desc)
    MapTheme.Fiord -> stringResource(R.string.map_theme_fiord_desc)
    MapTheme.OsmFr -> stringResource(R.string.map_theme_osm_fr_desc)
    MapTheme.Hot -> stringResource(R.string.map_theme_hot_desc)
    MapTheme.Bright -> stringResource(R.string.map_theme_bright_desc)
    MapTheme.Liberty -> stringResource(R.string.map_theme_liberty_desc)
}

@Composable
private fun ThemeMode.displayLabel(): String = when (this) {
    ThemeMode.System -> stringResource(R.string.theme_mode_system)
    ThemeMode.Light -> stringResource(R.string.theme_mode_light)
    ThemeMode.Dark -> stringResource(R.string.theme_mode_dark)
}

@Composable
private fun FuelCard.displayLabel(): String = when (this) {
    FuelCard.None -> stringResource(R.string.fuel_card_none)
    FuelCard.Routex -> stringResource(R.string.fuel_card_routex)
}

@StringRes
private fun poiProviderLabelRes(type: PoiProviderType): Int = when (type) {
    PoiProviderType.DataGouvElec -> R.string.provider_datagouv_elec
    PoiProviderType.Chargy -> R.string.provider_chargy
    PoiProviderType.OpenChargeMap -> R.string.provider_openchargemap
    PoiProviderType.Fastned -> R.string.provider_fastned
    PoiProviderType.Dkv -> R.string.provider_dkv
    PoiProviderType.EcoMovement -> R.string.provider_ecomovement
    PoiProviderType.Overpass -> R.string.provider_overpass
    PoiProviderType.Routex -> R.string.provider_routex
    PoiProviderType.Etalab -> R.string.provider_etalab
    PoiProviderType.GasApi -> R.string.provider_gasapi
    PoiProviderType.DataGouv -> R.string.provider_datagouv
    PoiProviderType.UkCma -> R.string.provider_uk_cma
    PoiProviderType.ItalyMimit -> R.string.provider_italy_mimit
    PoiProviderType.SloveniaGorivaSi -> R.string.provider_slovenia_goriva
    PoiProviderType.NorwayDrivstoffAppen -> R.string.provider_norway_drivstoff
    PoiProviderType.SwedenDrivstoffAppen -> R.string.provider_sweden_drivstoff
    PoiProviderType.PortugalDgeg -> R.string.provider_portugal_dgeg
    PoiProviderType.NetherlandsAnwb -> R.string.provider_netherlands_anwb
    PoiProviderType.DenmarkFuelpricesDk -> R.string.provider_denmark_fuelprices
    PoiProviderType.Fuelo -> R.string.provider_fuelo
    PoiProviderType.AustraliaNswFuelCheck -> R.string.provider_australia_nsw
    PoiProviderType.AustraliaFuelWatch -> R.string.provider_australia_fuelwatch
    PoiProviderType.AustraliaPetrolSpy -> R.string.provider_australia_petrolspy
    PoiProviderType.SwitzerlandComparis -> R.string.provider_switzerland_comparis
    PoiProviderType.CroatiaMzoe -> R.string.provider_croatia_mzoe
    PoiProviderType.FinlandPolttoaine -> R.string.provider_finland_polttoaine
    PoiProviderType.GreeceFuelGr -> R.string.provider_greece_fuelgr
    PoiProviderType.IrelandPickAPump -> R.string.provider_ireland_pickapump
    PoiProviderType.MoldovaAnre -> R.string.provider_moldova_anre
    PoiProviderType.RomaniaPeco -> R.string.provider_romania_peco
    PoiProviderType.SerbiaNis -> R.string.provider_serbia_nis
    PoiProviderType.MexicoCre -> R.string.provider_mexico_cre
    PoiProviderType.ArgentinaEnergia -> R.string.provider_argentina_energia
    PoiProviderType.OpenVanCamp -> R.string.provider_openvan_camp
    PoiProviderType.SpainMinetur -> R.string.provider_spain_minetur
    PoiProviderType.GermanyTankerkoenig -> R.string.provider_germany_tankerkoenig
    PoiProviderType.AustriaEControl -> R.string.provider_austria_econtrol
    PoiProviderType.BelgiumOfficial -> R.string.provider_belgium_official
    PoiProviderType.UsaEia -> R.string.provider_usa_eia
    else -> R.string.provider_overpass
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsScreen(
    settingsManager: SettingsManager,
    authManager: GoogleAuthManager?,
    errorLog: List<DetailedError>,
    onDismiss: () -> Unit,
    initialScreenStack: List<SettingsScreenPage>? = null,
    onInitialRouteConsumed: () -> Unit = {},
    onClearErrorLog: () -> Unit = {}
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
                            SettingsScreenPage.Main -> stringResource(R.string.screen_settings)
                            SettingsScreenPage.TollData -> stringResource(R.string.screen_highway_toll)
                            SettingsScreenPage.ErrorLog -> stringResource(R.string.screen_error_log)
                            SettingsScreenPage.About -> stringResource(R.string.screen_about)
                            SettingsScreenPage.VehicleConfig -> stringResource(R.string.screen_vehicle)
                            SettingsScreenPage.Sources -> stringResource(R.string.screen_sources)
                            SettingsScreenPage.Theme -> stringResource(R.string.screen_theme)
                            SettingsScreenPage.MapConfig -> stringResource(R.string.screen_map)
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
                SettingsScreenPage.ErrorLog -> ErrorLog(
                    errorLog = errorLog,
                    onClear = onClearErrorLog
                )
                SettingsScreenPage.About -> AboutContent(
                    settings = current,
                    onUpdate = { save(settingsManager, it) },
                    onShowDisclaimer = { showDisclaimer = true }
                )
                SettingsScreenPage.MapConfig -> MapConfig(
                    settings = current,
                    onUpdate = { save(settingsManager, it) }
                )
                SettingsScreenPage.Sources -> SourcesConfig(
                    settings = current,
                    onUpdate = { save(settingsManager, it) }
                )
                SettingsScreenPage.Theme -> ThemeConfig(
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
                stringResource(R.string.settings_map_engine),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MapEngine.entries.forEach { engine ->
                    FilterChip(
                        selected = settings.phoneMapEngine == engine,
                        onClick = { onUpdate(settings.copy(phoneMapEngine = engine)) },
                        label = { Text(engine.displayLabel()) },
                    )
                }
            }
        }

        if (settings.phoneMapEngine == MapEngine.MapLibre) {
            // Map Theme (for MapLibre)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.settings_map_theme),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                val previewCenter = remember(settings.lastKnownLat, settings.lastKnownLon) {
                    if (settings.lastKnownLat != null && settings.lastKnownLon != null) {
                        LatLng(settings.lastKnownLat, settings.lastKnownLon)
                    } else {
                        LatLng(48.8566, 2.3522) // Paris as default
                    }
                }

                MapTheme.entries.forEach { theme ->
                    val isSelected = settings.mapTheme == theme
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onUpdate(settings.copy(mapTheme = theme)) },
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Small map preview
                            Box(
                                modifier = Modifier
                                    .size(width = 90.dp, height = 70.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                MapLibreView(
                                    modifier = Modifier.fillMaxSize(),
                                    styleUrl = theme.styleUrl,
                                    cameraPosition = CameraPosition.Builder()
                                        .target(previewCenter)
                                        .zoom(9.5)
                                        .build()
                                )
                            }

                            // Title & Description
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)
                            ) {
                                Text(
                                    text = theme.displayLabel(),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = theme.displayDescription(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Selection Indicator
                            RadioButton(
                                selected = isSelected,
                                onClick = { onUpdate(settings.copy(mapTheme = theme)) }
                            )
                        }
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
                Text(stringResource(R.string.filter_show_traffic), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.filter_google_traffic), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text(stringResource(R.string.filter_debug_logging), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.filter_capture_network_logs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = settings.debugLoggingEnabled,
                onCheckedChange = { onUpdate(settings.copy(debugLoggingEnabled = it)) },
            )
        }

        if (BuildConfig.DEBUG) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.filter_simulate_premium), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.filter_simulate_premium_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.devSimulatePremium,
                    onCheckedChange = { onUpdate(settings.copy(devSimulatePremium = it)) },
                )
            }
        }

        // Itinerary
        Column {
            Text(
                stringResource(R.string.filter_itinerary),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                stringResource(R.string.filter_search_radius, settings.routeStationSearchRadiusMeters),
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
                    Text(stringResource(R.string.filter_only_highway), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.filter_only_highway_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val context = LocalContext.current
    data class ProviderUiInfo(
        val type: PoiProviderType,
        /** ISO country codes (e.g. "FR", "BE") or special values ("GLOBAL", "EU"). */
        val supportedCountries: List<String>,
        val providesFuel: Boolean = type.providesFuel,
        val providesElectric: Boolean = type.providesElectric,
        val providesSwap: Boolean = type.providesSwap,
    )

    fun providerLabel(type: PoiProviderType): String =
        context.getString(poiProviderLabelRes(type))

    fun countryLabel(code: String): String {
        val c = code.uppercase()
        return when (c) {
            "GLOBAL" -> context.getString(R.string.country_global)
            "EU" -> context.getString(R.string.country_europe)
            "PT-AC" -> context.getString(R.string.country_portugal_azores)
            "PT-MA" -> context.getString(R.string.country_portugal_madeira)
            "ES-CN" -> context.getString(R.string.country_spain_canary)
            "ES-IB" -> context.getString(R.string.country_spain_balearic)
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
        ProviderUiInfo(PoiProviderType.DataGouvElec, listOf("FR")),
        ProviderUiInfo(PoiProviderType.Chargy, listOf("LU")),
        ProviderUiInfo(PoiProviderType.OpenChargeMap, listOf("GLOBAL")),
        ProviderUiInfo(PoiProviderType.Fastned, listOf("GB")),
        ProviderUiInfo(PoiProviderType.Dkv, listOf("EU")),
        ProviderUiInfo(PoiProviderType.EcoMovement, listOf("GLOBAL")),
        ProviderUiInfo(PoiProviderType.Overpass, listOf("GLOBAL")),

        // Fuel
        ProviderUiInfo(PoiProviderType.Routex, listOf("EU")),
        ProviderUiInfo(PoiProviderType.Etalab, listOf("FR")),
        ProviderUiInfo(PoiProviderType.GasApi, listOf("FR")),
        ProviderUiInfo(PoiProviderType.DataGouv, listOf("FR")),
        ProviderUiInfo(PoiProviderType.UkCma, listOf("GB")),
        ProviderUiInfo(PoiProviderType.ItalyMimit, listOf("IT")),
        ProviderUiInfo(PoiProviderType.SloveniaGorivaSi, listOf("SI")),
        ProviderUiInfo(PoiProviderType.NorwayDrivstoffAppen, listOf("NO")),
        ProviderUiInfo(PoiProviderType.SwedenDrivstoffAppen, listOf("SE")),
        ProviderUiInfo(PoiProviderType.PortugalDgeg, listOf("PT")),
        ProviderUiInfo(PoiProviderType.NetherlandsAnwb, listOf("NL", "BE", "LU")),
        ProviderUiInfo(PoiProviderType.DenmarkFuelpricesDk, listOf("DK")),
        ProviderUiInfo(PoiProviderType.Fuelo, fueloSupported),
        ProviderUiInfo(PoiProviderType.AustraliaNswFuelCheck, listOf("AU-NSW")),
        ProviderUiInfo(PoiProviderType.AustraliaFuelWatch, listOf("AU-WA")),
        ProviderUiInfo(PoiProviderType.AustraliaPetrolSpy, listOf("AU")),
        ProviderUiInfo(PoiProviderType.SwitzerlandComparis, listOf("CH")),
        ProviderUiInfo(PoiProviderType.CroatiaMzoe, listOf("HR")),
        ProviderUiInfo(PoiProviderType.FinlandPolttoaine, listOf("FI")),
        ProviderUiInfo(PoiProviderType.GreeceFuelGr, listOf("GR")),
        ProviderUiInfo(PoiProviderType.IrelandPickAPump, listOf("IE")),
        ProviderUiInfo(PoiProviderType.MoldovaAnre, listOf("MD")),
        ProviderUiInfo(PoiProviderType.RomaniaPeco, listOf("RO")),
        ProviderUiInfo(PoiProviderType.SerbiaNis, listOf("RS")),
        ProviderUiInfo(PoiProviderType.MexicoCre, listOf("MX")),
        ProviderUiInfo(PoiProviderType.ArgentinaEnergia, listOf("AR")),
        ProviderUiInfo(
            PoiProviderType.OpenVanCamp,
            FuelPriceRegistry.REFERENCE_PRICE_COUNTRIES.toList().sorted(),
        ),
        ProviderUiInfo(PoiProviderType.SpainMinetur, listOf("ES")),
        ProviderUiInfo(PoiProviderType.GermanyTankerkoenig, listOf("DE")),
        ProviderUiInfo(PoiProviderType.AustriaEControl, listOf("AT")),
        ProviderUiInfo(PoiProviderType.BelgiumOfficial, listOf("BE")),
        ProviderUiInfo(PoiProviderType.UsaEia, listOf("US")),
    )
        .filter { it.type.isUserSelectablePoiDataSource() }
        .distinctBy { it.type }

    val providersByCountry: Map<String, List<ProviderUiInfo>> = remember(providers) {
        providers
            .flatMap { p -> p.supportedCountries.map { c -> c.uppercase() to p } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, list) ->
                list.distinctBy { it.type }.sortedBy { providerLabel(it.type).lowercase() }
            }
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
    var energyFilter by remember { mutableStateOf("all") }
    val filteredCountryKeys = remember(sortedCountryKeys, countryFilterText, energyFilter, providersByCountry) {
        val q = countryFilterText.trim().lowercase()
        val baseKeys = if (q.isEmpty()) sortedCountryKeys
        else sortedCountryKeys.filter { key ->
            countryLabel(key).lowercase().contains(q) ||
                key.lowercase().contains(q)
        }

        if (energyFilter == "all") baseKeys
        else baseKeys.filter { key ->
            providersByCountry[key].orEmpty().any { p ->
                when (energyFilter) {
                    "fuel" -> p.providesFuel
                    "electric" -> p.providesElectric
                    "swap" -> p.providesSwap
                    else -> true
                }
            }
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
                stringResource(R.string.filter_selection_mode),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(
                    selected = settings.poiProviderSelectionMode == PoiProviderSelectionMode.Manual,
                    onClick = { onUpdate(settings.copy(poiProviderSelectionMode = PoiProviderSelectionMode.Manual)) },
                    label = { Text(stringResource(R.string.action_manual)) }
                )
                FilterChip(
                    selected = settings.poiProviderSelectionMode == PoiProviderSelectionMode.Auto,
                    onClick = { onUpdate(settings.copy(poiProviderSelectionMode = PoiProviderSelectionMode.Auto)) },
                    label = { Text(stringResource(R.string.action_auto_by_country)) }
                )
            }
            if (settings.poiProviderSelectionMode == PoiProviderSelectionMode.Auto) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.filter_auto_sources_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column {
            Text(
                stringResource(R.string.filter_data_sources),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "all" to stringResource(R.string.action_all),
                    "fuel" to stringResource(R.string.search_mode_fuel),
                    "electric" to stringResource(R.string.search_mode_ev),
                    "swap" to "Swap"
                ).forEach { (id, label) ->
                    FilterChip(
                        selected = energyFilter == id,
                        onClick = { energyFilter = id },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            OutlinedTextField(
                value = countryFilterText,
                onValueChange = { countryFilterText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.filter_by_country)) },
                placeholder = { Text(stringResource(R.string.filter_country_placeholder)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            if (filteredCountryKeys.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.filter_no_country_match),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            filteredCountryKeys.forEach { countryKey ->
                val list = providersByCountry[countryKey].orEmpty().filter { p ->
                    when (energyFilter) {
                        "fuel" -> p.providesFuel
                        "electric" -> p.providesElectric
                        "swap" -> p.providesSwap
                        else -> true
                    }
                }
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
                                    allOn -> stringResource(R.string.filter_sources_on, list.size)
                                    selectedInCountry == 0 -> stringResource(R.string.filter_sources_off, list.size)
                                    else -> stringResource(R.string.filter_sources_partial, selectedInCountry, list.size)
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
                                    .thenBy { providerLabel(it.type).lowercase() }
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
                                            providerLabel(p.type),
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
private fun ThemeConfig(
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
        Column {
            Text(
                stringResource(R.string.screen_app_theme),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.uiThemeMode == mode,
                        onClick = { onUpdate(settings.copy(uiThemeMode = mode)) },
                        label = { Text(mode.displayLabel()) },
                    )
                }
            }
            Text(
                stringResource(R.string.settings_theme_night_maps),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

private fun save(settingsManager: SettingsManager, s: AppSettings) {
    settingsManager.saveSettingsWithThemeCheck(s)
}

@Composable
private fun PremiumSubscriptionNoticeCard(notice: PremiumSubscriptionNotice) {
    val (containerColor, contentColor, message) = when (notice) {
        PremiumSubscriptionNotice.BillingIssue -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            stringResource(R.string.premium_billing_issue),
        )
        is PremiumSubscriptionNotice.ExpiresOn -> {
            val formattedDate = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(notice.expirationDateMillis))
            Triple(
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.onTertiaryContainer,
                stringResource(R.string.premium_expires_on, formattedDate),
            )
        }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = contentColor,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
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
    val billingManager = koinInject<BillingManager>()
    val subscriptionNotice by billingManager.subscriptionNotice.collectAsState()
    var showClearCacheConfirm by remember { mutableStateOf(false) }

    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text(stringResource(R.string.settings_clear_cache_title)) },
            text = { Text(stringResource(R.string.settings_clear_cache_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheConfirm = false
                        scope.launch {
                            CacheManager.clearAllCaches(context)
                            snackbarHostState.showSnackbar(context.getString(R.string.cache_cleared))
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_clear), color = Color(0xFFFF6B6B))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
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
            if (settings.hasPremiumFeatures) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1D4ED8)),
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
                            val title = when {
                                settings.isPremium -> stringResource(R.string.premium_active)
                                else -> stringResource(R.string.premium_simulated)
                            }
                            val subtitle = when {
                                settings.isPremium -> stringResource(R.string.premium_thanks)
                                else -> stringResource(R.string.premium_dev_override)
                            }
                            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
            }

            if (settings.isPremium && subscriptionNotice != null) {
                PremiumSubscriptionNoticeCard(notice = subscriptionNotice!!)
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                ListItem(
                    headlineContent = {
                        Text(
                            text = if (settings.isLoggedIn) {
                                stringResource(R.string.hello_user, settings.googleUserName.orEmpty())
                            } else {
                                stringResource(R.string.not_signed_in)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    supportingContent = {
                        Text(
                            text = if (settings.isLoggedIn) {
                                stringResource(R.string.google_account_connected)
                            } else {
                                stringResource(R.string.sign_in_sync_profile)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        when {
                            authManager == null -> Text(stringResource(R.string.settings_auth_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            settings.isLoggedIn -> {
                                TextButton(
                                    onClick = {
                                        authManager.signOut { success ->
                                            if (!success) {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        context.getString(R.string.sign_out_failed)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                ) { Text(stringResource(R.string.action_sign_out)) }
                            }
                            else -> {
                                Button(
                                    onClick = {
                                        authManager.signIn(context) { success, error ->
                                            if (!success) {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        error ?: context.getString(R.string.sign_in_failed)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                ) { Text(stringResource(R.string.action_sign_in)) }
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
                    label = stringResource(R.string.screen_vehicle),
                    value = if (settings.vehicleBrand.isNotEmpty()) {
                        "${settings.vehicleBrand} ${settings.vehicleModel}"
                    } else {
                        stringResource(R.string.vehicle_not_configured)
                    },
                    onClick = { onNavigate(SettingsScreenPage.VehicleConfig) }
                )
                SettingsItem(
                    label = stringResource(R.string.screen_map),
                    value = stringResource(R.string.settings_map_subtitle),
                    onClick = { onNavigate(SettingsScreenPage.MapConfig) }
                )
                SettingsItem(
                    label = stringResource(R.string.screen_sources),
                    value = when (settings.poiProviderSelectionMode) {
                        PoiProviderSelectionMode.Auto -> stringResource(R.string.action_auto_by_country)
                        PoiProviderSelectionMode.Manual -> stringResource(R.string.selection_manual_full)
                    },
                    onClick = { onNavigate(SettingsScreenPage.Sources) }
                )
                SettingsItem(
                    label = stringResource(R.string.screen_highway_toll),
                    value = if (!settings.tollDataPath.isNullOrBlank()) {
                        stringResource(R.string.toll_downloaded)
                    } else {
                        stringResource(R.string.toll_not_downloaded)
                    },
                    onClick = { onNavigate(SettingsScreenPage.TollData) }
                )
                SettingsItem(
                    label = stringResource(R.string.screen_error_log),
                    value = stringResource(R.string.settings_error_log_subtitle),
                    onClick = { onNavigate(SettingsScreenPage.ErrorLog) }
                )
                SettingsItem(
                    label = stringResource(R.string.screen_theme),
                    value = settings.uiThemeMode.displayLabel(),
                    onClick = { onNavigate(SettingsScreenPage.Theme) }
                )
                SettingsItem(
                    label = stringResource(R.string.screen_about),
                    value = stringResource(R.string.settings_about_subtitle),
                    onClick = { onNavigate(SettingsScreenPage.About) }
                )
                SettingsItem(
                    label = stringResource(R.string.screen_clear_cache),
                    value = stringResource(R.string.settings_clear_cache_subtitle),
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

    val fileInfo = remember(settings.tollDataPath, context) {
        settings.tollDataPath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                val size = file.length()
                val lastModified = file.lastModified()
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastModified))
                val sizeStr = if (size > 1024 * 1024) "${size / (1024 * 1024)} MB" else "${size / 1024} KB"
                context.getString(R.string.toll_file_info, sizeStr, dateStr)
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
            text = stringResource(R.string.toll_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            text = if (downloaded) {
                stringResource(R.string.toll_status_downloaded)
            } else {
                stringResource(R.string.toll_status_not_downloaded)
            },
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
            text = stringResource(R.string.toll_path, displayPath),
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
                                            downloadError = e.message
                                                ?: context.getString(R.string.download_failed)
                                        }
                                    }
                                )
                            }
                        },
                    ) {
                        Text(stringResource(R.string.screen_download_toll_data))
                    }
                }

                else -> {
                    val (bytes, total) = progress
                    val pct = if (total != null && total > 0) (100 * bytes / total).toInt() else null
                    Text(
                        text = if (pct != null) {
                            stringResource(R.string.toll_downloading_percent, pct)
                        } else {
                            stringResource(R.string.toll_downloading_mb, bytes / (1024 * 1024))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        downloadError?.let { err ->
            Text(
                text = stringResource(R.string.toll_error, err),
                color = Color(0xFFFF6B6B),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun AboutContent(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit,
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
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(24.dp))
        AboutRow(stringResource(R.string.about_version_name), BuildConfig.VERSION_NAME)
        AboutRow(stringResource(R.string.about_version_code), BuildConfig.VERSION_CODE.toString())
        AboutRow(stringResource(R.string.about_build_date), BuildConfig.BUILD_DATE)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.about_premium_mode),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    stringResource(R.string.about_premium_mode_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = settings.devSimulatePremium,
                onCheckedChange = { onUpdate(settings.copy(devSimulatePremium = it)) },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        AboutRowClickable(
            label = stringResource(id = R.string.about_view_disclaimer),
            onClick = onShowDisclaimer
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.about_used_apis),
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
                attributionRes = api.attributionRes,
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
    @StringRes attributionRes: Int? = null,
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
            if (attributionRes != null) {
                Text(
                    text = stringResource(attributionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = stringResource(R.string.action_open_website),
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
                    stringResource(R.string.vehicle_identity),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                ConfigTextField(
                    label = stringResource(R.string.vehicle_brand),
                    value = settings.vehicleBrand,
                    leadingIcon = Icons.Default.DirectionsCar
                ) { onUpdate(settings.copy(vehicleBrand = it)) }
                ConfigTextField(
                    label = stringResource(R.string.vehicle_model),
                    value = settings.vehicleModel,
                    leadingIcon = Icons.Default.Badge
                ) { onUpdate(settings.copy(vehicleModel = it)) }

                Text(
                    stringResource(R.string.screen_vehicle_type),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    fr.geoking.gaston.VehicleType.entries.forEach { type ->
                        val icon = when (type) {
                            fr.geoking.gaston.VehicleType.Car -> Icons.Default.DirectionsCar
                            fr.geoking.gaston.VehicleType.Truck -> Icons.Default.LocalShipping
                            fr.geoking.gaston.VehicleType.Motorcycle -> Icons.Default.TwoWheeler
                            fr.geoking.gaston.VehicleType.Motorhome -> Icons.Default.Home
                            fr.geoking.gaston.VehicleType.Bicycle -> Icons.Default.DirectionsBike
                        }
                        val label = when (type) {
                            fr.geoking.gaston.VehicleType.Car -> stringResource(R.string.vehicle_type_car)
                            fr.geoking.gaston.VehicleType.Truck -> stringResource(R.string.vehicle_type_truck)
                            fr.geoking.gaston.VehicleType.Motorcycle -> stringResource(R.string.vehicle_type_motorcycle)
                            fr.geoking.gaston.VehicleType.Motorhome -> stringResource(R.string.vehicle_type_motorhome)
                            fr.geoking.gaston.VehicleType.Bicycle -> stringResource(R.string.vehicle_type_bicycle)
                        }
                        FilterChip(
                            selected = settings.vehicleType == type,
                            onClick = { onUpdate(settings.copy(vehicleType = type)) },
                            label = { Text(label) },
                            leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) }
                        )
                    }
                }

                Text(
                    stringResource(R.string.vehicle_energy_type),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "gas" to (R.string.energy_gas to Icons.Default.LocalGasStation),
                        "electric" to (R.string.vehicle_energy_electric to Icons.Default.EvStation),
                        "hybrid" to (R.string.energy_hybrid to Icons.Default.SettingsInputComponent),
                    ).forEach { (id, info) ->
                        val (labelRes, icon) = info
                        FilterChip(
                            selected = settings.vehicleEnergy == id,
                            onClick = { onUpdate(settings.copy(vehicleEnergy = id)) },
                            label = { Text(stringResource(labelRes)) },
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
                    stringResource(R.string.vehicle_specifications),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (settings.vehicleEnergy == "gas" || settings.vehicleEnergy == "hybrid") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ConfigTextField(
                            label = stringResource(R.string.vehicle_tank_liters),
                            value = settings.gasTankCapacityLiters?.toString() ?: "",
                            modifier = Modifier.weight(1f),
                            leadingIcon = Icons.Default.WaterDrop,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        ) { onUpdate(settings.copy(gasTankCapacityLiters = it.toFloatOrNull())) }

                        ConfigTextField(
                            label = stringResource(R.string.vehicle_consumption_l100),
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
                            label = stringResource(R.string.vehicle_battery_kwh),
                            value = settings.batteryCapacityKwh?.toString() ?: "",
                            modifier = Modifier.weight(1f),
                            leadingIcon = Icons.Default.BatteryChargingFull,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        ) { onUpdate(settings.copy(batteryCapacityKwh = it.toFloatOrNull())) }

                        ConfigTextField(
                            label = stringResource(R.string.vehicle_range_km_label),
                            value = settings.evRangeKm.toString(),
                            modifier = Modifier.weight(1f),
                            leadingIcon = Icons.Default.Map,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        ) { onUpdate(settings.copy(evRangeKm = it.toIntOrNull() ?: 300)) }
                    }
                    ConfigTextField(
                        label = stringResource(R.string.vehicle_consumption_kwh100),
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
                    stringResource(R.string.vehicle_preferences),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (settings.vehicleEnergy == "gas" || settings.vehicleEnergy == "hybrid") {
                    Text(
                        stringResource(R.string.vehicle_preferred_gas_types),
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
                        stringResource(R.string.vehicle_fuel_card),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    FuelCard.entries.forEach { card ->
                        SelectionItem(
                            label = card.displayLabel(),
                            isSelected = settings.fuelCard == card,
                            onSelect = { onUpdate(settings.copy(fuelCard = card)) }
                        )
                    }
                }

                if (settings.vehicleEnergy == "electric" || settings.vehicleEnergy == "hybrid") {
                    Text(
                        stringResource(R.string.vehicle_preferred_power_range),
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
private fun ErrorLog(
    errorLog: List<DetailedError>,
    onClear: () -> Unit
) {
    val scrollState = rememberScrollState()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val genericErrorLabel = stringResource(R.string.error_log_generic)
    val reversedLog = remember(errorLog) { errorLog.reversed() }
    var showClearConfirm by remember { mutableStateOf(false) }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.settings_clear_error_log_title)) },
            text = { Text(stringResource(R.string.settings_clear_error_log_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClear()
                        showClearConfirm = false
                    }
                ) {
                    Text(stringResource(R.string.action_clear), color = Color(0xFFFF6B6B))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    SelectionContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(20.dp)
        ) {
            if (reversedLog.isEmpty()) {
                Text(stringResource(R.string.settings_no_errors), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            val allErrors = reversedLog.joinToString("\n\n") { error ->
                                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(error.timestamp))
                                val httpCode = error.httpCode?.let { context.getString(R.string.error_log_http, it) }
                                    ?: genericErrorLabel
                                "[$timestamp] $httpCode\n${error.message}"
                            }
                            scope.launch {
                                clipboard.setClipEntry(androidx.compose.ui.platform.ClipEntry(android.content.ClipData.newPlainText("", allErrors)))
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.action_copy_all))
                    }

                    OutlinedButton(
                        onClick = { showClearConfirm = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6B6B))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_clear_logs))
                    }
                }

                reversedLog.forEach { error ->
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(error.timestamp))
                    val httpCode = error.httpCode?.let { stringResource(R.string.error_log_http, it) }
                        ?: genericErrorLabel

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
                                    contentDescription = stringResource(R.string.cd_copy_error),
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
