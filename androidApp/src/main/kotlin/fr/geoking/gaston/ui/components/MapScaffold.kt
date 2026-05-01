package fr.geoking.gaston.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import fr.geoking.gaston.AppSettings
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.effectiveIrvePowerLevels
import fr.geoking.gaston.effectiveMapEnergyFilterIds
import fr.geoking.gaston.poi.PoiProviderType
import fr.geoking.gaston.poi.anyProvidesElectric
import fr.geoking.gaston.poi.anyProvidesFuel
import fr.geoking.gaston.ui.ColorHelper
import fr.geoking.gaston.ui.MAP_ENERGY_OPTIONS
import fr.geoking.gaston.ui.MAP_IRVE_POWER_OPTIONS
import fr.geoking.gaston.ui.anim.AnimationPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScaffold(
    title: String,
    settingsManager: SettingsManager,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLocateMe: () -> Unit,
    onShowSettings: () -> Unit,
    onPlanRoute: (() -> Unit)? = null,
    onLocatePlace: (() -> Unit)? = null,
    onRouteToDirection: (() -> Unit)? = null,
    showFavoritesOnly: Boolean = false,
    onShowFavoritesOnlyChange: ((Boolean) -> Unit)? = null,
    favoritesFilterEnabled: Boolean = false,
    isLoading: Boolean = false,
    palette: AnimationPalette? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val settings by settingsManager.settings.collectAsState()
    val selectedProviders = settings.selectedPoiProviders
    var navMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(title, color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        if (onPlanRoute != null || onLocatePlace != null || onRouteToDirection != null) {
                            Box {
                                IconButton(onClick = { navMenuExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Directions,
                                        contentDescription = "Navigation",
                                        tint = Color.White
                                    )
                                }
                                DropdownMenu(
                                    expanded = navMenuExpanded,
                                    onDismissRequest = { navMenuExpanded = false }
                                ) {
                                    if (onPlanRoute != null) {
                                        DropdownMenuItem(
                                            text = { Text("Plan route") },
                                            leadingIcon = { Icon(Icons.Default.Directions, contentDescription = null) },
                                            onClick = {
                                                navMenuExpanded = false
                                                onPlanRoute()
                                            }
                                        )
                                    }
                                    if (onLocatePlace != null) {
                                        DropdownMenuItem(
                                            text = { Text("Locate a place") },
                                            leadingIcon = { Icon(Icons.Default.Place, contentDescription = null) },
                                            onClick = {
                                                navMenuExpanded = false
                                                onLocatePlace()
                                            }
                                        )
                                    }
                                    if (onRouteToDirection != null) {
                                        DropdownMenuItem(
                                            text = { Text("Route to a direction") },
                                            leadingIcon = { Icon(Icons.Default.Directions, contentDescription = null) },
                                            onClick = {
                                                navMenuExpanded = false
                                                onRouteToDirection()
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        IconButton(onClick = onRefresh) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh map",
                                tint = Color.White
                            )
                        }

                        IconButton(onClick = onShowSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Map settings",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0F172A)
                    )
                )

                // Unified Filter Bar
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A))
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    item {
                        FilterChip(
                            selected = false,
                            onClick = onShowSettings,
                            label = {
                                Text(
                                    if (selectedProviders.isEmpty()) "No Source"
                                    else if (selectedProviders.size == 1) {
                                        when (selectedProviders.first()) {
                                            PoiProviderType.Routex -> "Source: Routex"
                                            PoiProviderType.Etalab -> "Source: France (official)"
                                            PoiProviderType.GasApi -> "Source: Gas API"
                                            PoiProviderType.DataGouv -> "Source: France (official)"
                                            PoiProviderType.UkCma -> "Source: UK Fuel Finder"
                                            PoiProviderType.ItalyMimit -> "Source: MIMIT (Italy)"
                                            PoiProviderType.SloveniaGorivaSi -> "Source: goriva.si (Slovenia)"
                                            PoiProviderType.NorwayDrivstoffAppen -> "Source: DrivstoffAppen (Norway)"
                                            PoiProviderType.SwedenDrivstoffAppen -> "Source: DrivstoffAppen / bensinpriser.nu (Sweden)"
                                            PoiProviderType.PortugalDgeg -> "Source: DGEG (Portugal)"
                                            PoiProviderType.NetherlandsAnwb -> "Source: ANWB (NL/BE/LU)"
                                            PoiProviderType.DenmarkFuelpricesDk -> "Source: Fuelprices.dk (Denmark)"
                                            PoiProviderType.Fuelo -> "Source: Fuelo.net"
                                            PoiProviderType.AustraliaNswFuelCheck -> "Source: FuelCheck (NSW AU)"
                                            PoiProviderType.CroatiaMzoe -> "Source: MZOE (Croatia)"
                                            PoiProviderType.FinlandPolttoaine -> "Source: Polttoaine.net (Finland)"
                                            PoiProviderType.GreeceFuelGr -> "Source: FuelGR (Greece)"
                                            PoiProviderType.IrelandPickAPump -> "Source: Pick A Pump (Ireland)"
                                            PoiProviderType.MoldovaAnre -> "Source: ANRE (Moldova)"
                                            PoiProviderType.RomaniaPeco -> "Source: Peco Online (Romania)"
                                            PoiProviderType.SerbiaNis -> "Source: NIS (Serbia)"
                                            PoiProviderType.MexicoCre -> "Source: CRE (Mexico)"
                                            PoiProviderType.ArgentinaEnergia -> "Source: Energía (Argentina)"
                                            PoiProviderType.DataGouvElec -> "Source: IRVE"
                                            PoiProviderType.OpenChargeMap -> "Source: Open Charge Map"
                                            PoiProviderType.Chargy -> "Source: Chargy (real-time)"
                                            PoiProviderType.Fastned -> "Source: Fastned (UK)"
                                            PoiProviderType.Dkv -> "Source: DKV Mobility"
                                            PoiProviderType.EcoMovement -> "Source: Eco-Movement"
                                            PoiProviderType.OpenVanCamp -> "Source: OpenVan.camp (LU, HR, SI...)"
                                            PoiProviderType.SpainMinetur -> "Source: Spain Minetur (official)"
                                            PoiProviderType.GermanyTankerkoenig -> "Source: Tankerkönig (Germany)"
                                            PoiProviderType.AustriaEControl -> "Source: E-Control (Austria)"
                                            PoiProviderType.BelgiumOfficial -> "Source: Belgium (official)"
                                            PoiProviderType.Overpass -> "Source: OSM + data.gouv (camping, picnic…)"
                                            PoiProviderType.Hybrid -> "Source: Hybrid (Gas + EV)"
                                        }
                                    } else "Sources (${selectedProviders.size})"
                                )
                            }
                        )
                    }

                    if (selectedProviders.anyProvidesFuel()) {
                        items(MAP_ENERGY_OPTIONS.filter { it.first != "electric" }) { (id, label) ->
                            val isSelected = settings.effectiveMapEnergyFilterIds().contains(id)
                            val color = ColorHelper.getFuelColor(id) ?: MaterialTheme.colorScheme.primary
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    val current = settings.selectedMapEnergyTypes
                                    val next = if (current.contains(id)) current - id else current + id
                                    settingsManager.setUseVehicleFilter(false)
                                    settingsManager.setMapEnergyTypes(next)
                                },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = color,
                                    selectedLabelColor = Color.White,
                                    iconColor = color,
                                    selectedLeadingIconColor = Color.White
                                ),
                                leadingIcon = {
                                    Box(modifier = Modifier.size(12.dp).background(color, MaterialTheme.shapes.small))
                                }
                            )
                        }
                    }

                    if (selectedProviders.anyProvidesElectric()) {
                        items(MAP_IRVE_POWER_OPTIONS) { (kw, label) ->
                            val isSelected = settings.effectiveIrvePowerLevels().contains(kw)
                            val color = ColorHelper.getPowerColorByLevel(kw)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    val current = settings.mapPowerLevels
                                    val next = if (current.contains(kw)) current - kw else current + kw
                                    settingsManager.setUseVehicleFilter(false)
                                    settingsManager.setMapPowerLevels(next)
                                },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = color,
                                    selectedLabelColor = Color.White,
                                    iconColor = color,
                                    selectedLeadingIconColor = Color.White
                                ),
                                leadingIcon = {
                                    Box(modifier = Modifier.size(12.dp).background(color, MaterialTheme.shapes.small))
                                }
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (onShowFavoritesOnlyChange != null) {
                    FilterFab(
                        settingsManager = settingsManager,
                        favoritesFilterEnabled = favoritesFilterEnabled,
                        showFavoritesOnly = showFavoritesOnly,
                        onShowFavoritesOnlyChange = onShowFavoritesOnlyChange
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            content(padding)

            if (isLoading && palette != null) {
                MapLoader(
                    palette = palette,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(1f)
                )
            }
        }
    }
}
