package fr.geoking.gaston.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.geoking.gaston.AppSettings
import fr.geoking.gaston.BuildConfig
import fr.geoking.gaston.PoiProviderSelectionMode
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.effectiveAllowedCategories
import fr.geoking.gaston.effectiveEnergyFilterMode
import fr.geoking.gaston.effectiveMapEnergyFilterIds
import fr.geoking.gaston.api.geocoding.GeocodedPlace
import fr.geoking.gaston.api.geocoding.GeocodingClient
import fr.geoking.gaston.intent.NavDestination
import fr.geoking.gaston.poi.EnergyFilterMode
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProviderType
import fr.geoking.gaston.poi.anyProvidesElectric
import fr.geoking.gaston.poi.anyProvidesFuel
import fr.geoking.gaston.repository.FuelForecastRepository
import fr.geoking.gaston.repository.FuelForecastUiState
import fr.geoking.gaston.ui.SettingsScreenPage
import fr.geoking.gaston.ui.components.AdMobBanner
import fr.geoking.gaston.ui.components.CheapestStationsCard
import fr.geoking.gaston.ui.components.EnergyTypeSelectorRows

private enum class QuickActionType { Fuel, EV, Hybrid }

private data class DashboardRow(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val type: QuickActionType? = null
)

private fun QuickActionType.toEnergyFilterMode(): EnergyFilterMode = when (this) {
    QuickActionType.Fuel -> EnergyFilterMode.Fuel
    QuickActionType.EV -> EnergyFilterMode.Electric
    QuickActionType.Hybrid -> EnergyFilterMode.Hybrid
}

private fun EnergyFilterMode.toQuickActionType(): QuickActionType = when (this) {
    EnergyFilterMode.Fuel -> QuickActionType.Fuel
    EnergyFilterMode.Electric -> QuickActionType.EV
    EnergyFilterMode.Hybrid -> QuickActionType.Hybrid
}

private fun cityLabelFromGeocodedPlace(place: GeocodedPlace): String {
    val raw = place.label.trim()
    if (raw.isBlank()) return raw
    // Labels typically look like "Paris, Île-de-France, France" (or similar). We only want the city.
    return raw.substringBefore(',').trim().ifBlank { raw }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneDashboardMainContent(
    modifier: Modifier = Modifier,
    geocodingClient: GeocodingClient?,
    hasLocationPermission: Boolean,
    userLat: Double?,
    userLon: Double?,
    selectedSearchLocation: GeocodedPlace?,
    settings: AppSettings,
    settingsManager: SettingsManager,
    providers: Set<PoiProviderType>,
    energyFilterIds: Set<String>,
    isLoadingPois: Boolean,
    showLoaderByDelay: Boolean,
    nearbyPois: List<Poi>,
    searchError: String?,
    mapDepsReady: Boolean,
    fuelForecastRepository: FuelForecastRepository?,
    fuelForecastState: FuelForecastUiState,
    fuelForecastLoading: Boolean,
    onOpenMap: (Poi?) -> Unit,
    onOpenRoutes: (NavDestination?) -> Unit,
    onOpenFuelForecast: () -> Unit,
    onOpenEmergency: () -> Unit,
    onOpenSettings: (List<SettingsScreenPage>?) -> Unit,
    onOpenNetworkDiagnostics: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onLocationSelected: (GeocodedPlace?) -> Unit,
    onPoiSelected: (Poi) -> Unit
) {
    val isParkingSelected = remember(settings) {
        settings.poiProviderSelectionMode == fr.geoking.gaston.PoiProviderSelectionMode.Manual &&
            settings.selectedPoiProviders == setOf(fr.geoking.gaston.poi.PoiProviderType.Overpass) &&
            settings.selectedOverpassAmenityTypes == setOf("parking")
    }

    val currentEnergyMode = remember(settings) {
        settings.effectiveEnergyFilterMode().toQuickActionType()
    }

    val quickActions = listOf(
        DashboardRow(
            title = "Fuel",
            subtitle = "Gas stations",
            icon = Icons.Default.LocalGasStation,
            type = QuickActionType.Fuel,
            onClick = {
                if (currentEnergyMode != QuickActionType.Fuel) {
                    settingsManager.setEnergyFilterMode(EnergyFilterMode.Fuel)
                }
            }
        ),
        DashboardRow(
            title = "EV",
            subtitle = "Charging",
            icon = Icons.Default.EvStation,
            type = QuickActionType.EV,
            onClick = {
                if (currentEnergyMode != QuickActionType.EV) {
                    settingsManager.setEnergyFilterMode(EnergyFilterMode.Electric)
                }
            }
        ),
        DashboardRow(
            title = "Hybrid",
            subtitle = "Both",
            icon = Icons.Default.Map,
            type = QuickActionType.Hybrid,
            onClick = {
                if (currentEnergyMode != QuickActionType.Hybrid) {
                    settingsManager.setEnergyFilterMode(EnergyFilterMode.Hybrid)
                }
            }
        )
    )

    val otherActions = listOf(
        DashboardRow(
            title = "My car settings",
            subtitle = if (settings.vehicleBrand.isNotEmpty()) "${settings.vehicleBrand} ${settings.vehicleModel}" else "Configure your vehicle",
            icon = Icons.Default.DirectionsCar,
            onClick = { onOpenSettings(listOf(SettingsScreenPage.VehicleConfig)) }
        ),
        DashboardRow(
            title = "Network & location",
            subtitle = "Diagnostics",
            icon = Icons.Default.SignalCellular4Bar,
            onClick = onOpenNetworkDiagnostics
        ),
        DashboardRow(
            title = "About",
            subtitle = "App info",
            icon = Icons.Default.Info,
            onClick = { onOpenSettings(listOf(SettingsScreenPage.About)) }
        ),
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            PhoneDashboardDestinationSearch(
                geocodingClient = geocodingClient,
                hasLocationPermission = hasLocationPermission,
                userLat = userLat,
                userLon = userLon,
                selectedSearchLocation = selectedSearchLocation,
                settings = settings,
                onLocationSelected = onLocationSelected,
                onOpenRoutes = onOpenRoutes
            )
        }

        item {
            PhoneDashboardQuickModeRow(
                currentEnergyMode = currentEnergyMode,
                quickActions = quickActions
            )
        }

        item {
            EnergyTypeSelectorRows(
                settings = settings,
                settingsManager = settingsManager,
                providers = providers
            )
        }

        item {
            PhoneDashboardNearbyCheapestSection(
                isLoadingPois = isLoadingPois,
                showLoaderByDelay = showLoaderByDelay,
                hasLocationPermission = hasLocationPermission,
                onRequestLocationPermission = onRequestLocationPermission,
                nearbyPois = nearbyPois,
                userLat = userLat,
                userLon = userLon,
                energyFilterIds = energyFilterIds,
                searchError = searchError,
                selectedSearchLocation = selectedSearchLocation,
                isParkingSelected = isParkingSelected,
                onPoiSelected = onPoiSelected,
                onOpenMap = onOpenMap
            )
        }

        item {
            PhoneDashboardParkingRouteRow(
                settings = settings,
                settingsManager = settingsManager,
                mapDepsReady = mapDepsReady,
                isParkingSelected = isParkingSelected,
                onOpenRoutes = onOpenRoutes
            )
        }

        if (fuelForecastRepository != null) {
            item {
                PhoneDashboardFuelForecastCard(
                    fuelForecastLoading = fuelForecastLoading,
                    fuelForecastState = fuelForecastState,
                    onOpenFuelForecast = onOpenFuelForecast
                )
            }
        }

        item {
            PhoneDashboardEmergencyCard(onOpenEmergency = onOpenEmergency)
        }

        item {
            PhoneDashboardOtherActionsGrid(otherActions = otherActions)
        }

        item {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneDashboardQuickModeRow(
    currentEnergyMode: QuickActionType,
    quickActions: List<DashboardRow>
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        quickActions.forEach { action ->
            val isSelected = action.type == currentEnergyMode
            Card(
                onClick = action.onClick,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                ),
                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = action.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = action.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PhoneDashboardNearbyCheapestSection(
    isLoadingPois: Boolean,
    showLoaderByDelay: Boolean,
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit,
    nearbyPois: List<Poi>,
    userLat: Double?,
    userLon: Double?,
    energyFilterIds: Set<String>,
    searchError: String?,
    selectedSearchLocation: GeocodedPlace?,
    isParkingSelected: Boolean,
    onPoiSelected: (Poi) -> Unit,
    onOpenMap: (Poi?) -> Unit
) {
    if (isLoadingPois && showLoaderByDelay) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (isParkingSelected) "Parkings les plus proches" else "Nearby cheapest",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Start).padding(bottom = 12.dp)
                )
                Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (isParkingSelected) "Recherche de parkings..." else "Searching nearby...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    } else {
        val cardModifier = if (!hasLocationPermission) {
            Modifier
                .fillMaxWidth()
                .clickable { onRequestLocationPermission() }
        } else {
            Modifier.fillMaxWidth()
        }
        CheapestStationsCard(
            stations = nearbyPois,
            userLatitude = userLat,
            userLongitude = userLon,
            selectedEnergyIds = energyFilterIds,
            onClick = onPoiSelected,
            onMapClick = { onOpenMap(null) },
            modifier = cardModifier,
            emptyMessage = searchError,
            title = when {
                isParkingSelected -> "Parkings les plus proches"
                selectedSearchLocation != null -> "Cheapest near ${cityLabelFromGeocodedPlace(selectedSearchLocation)}"
                else -> "Nearby cheapest"
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneDashboardParkingRouteRow(
    settings: AppSettings,
    settingsManager: SettingsManager,
    mapDepsReady: Boolean,
    isParkingSelected: Boolean,
    onOpenRoutes: (NavDestination?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            onClick = {
                if (isParkingSelected) {
                    settingsManager.setPoiProviderSelectionMode(fr.geoking.gaston.PoiProviderSelectionMode.Auto)
                    settingsManager.setUseVehicleFilter(true)
                } else {
                    settingsManager.setUseVehicleFilter(false)
                    settingsManager.setPoiProviderSelectionMode(fr.geoking.gaston.PoiProviderSelectionMode.Manual)
                    settingsManager.setPoiProviderTypes(setOf(fr.geoking.gaston.poi.PoiProviderType.Overpass))
                    settingsManager.setOverpassAmenityTypes(setOf("parking"))
                }
            },
            modifier = Modifier
                .weight(1f)
                .height(96.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isParkingSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
            ),
            border = if (isParkingSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocalParking,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        text = "Parking",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Nearby lots",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Card(
            onClick = { onOpenRoutes(null) },
            enabled = mapDepsReady,
            modifier = Modifier
                .weight(1f)
                .height(96.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Directions,
                    contentDescription = null,
                    tint = if (mapDepsReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.size(36.dp)
                )
                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        text = "Route",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (mapDepsReady) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                    Text(
                        text = "Plan a journey",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (mapDepsReady) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneDashboardEmergencyCard(onOpenEmergency: () -> Unit) {
    val emergencyRed = Color(0xFFD32F2F)
    val onEmergency = Color.White
    Card(
        onClick = onOpenEmergency,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = emergencyRed),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Sos,
                contentDescription = null,
                tint = onEmergency,
                modifier = Modifier.size(36.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Emergency",
                    style = MaterialTheme.typography.titleMedium,
                    color = onEmergency,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Locate yourself · share location · call 112",
                    style = MaterialTheme.typography.bodySmall,
                    color = onEmergency.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneDashboardFuelForecastCard(
    fuelForecastLoading: Boolean,
    fuelForecastState: FuelForecastUiState,
    onOpenFuelForecast: () -> Unit
) {
    Card(
        onClick = onOpenFuelForecast,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        ListItem(
            headlineContent = { Text("Price estimation") },
            supportingContent = { Text("Local estimate from market + nearby pumps") },
            leadingContent = {
                Icon(
                    imageVector = Icons.Default.LocalGasStation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            },
            trailingContent = {
                if (fuelForecastLoading && fuelForecastState.historyPoints.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    val price = fuelForecastState.historyPoints.lastOrNull()?.priceEurPerL
                    Text(
                        text = if (price != null) "€%.3f".format(price) else "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneDashboardOtherActionsGrid(otherActions: List<DashboardRow>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        otherActions.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pair.forEach { action ->
                    Card(
                        onClick = action.onClick,
                        enabled = action.enabled,
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        ListItem(
                            headlineContent = { Text(action.title, style = MaterialTheme.typography.titleSmall) },
                            supportingContent = {
                                Text(
                                    action.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingContent = {
                                Icon(
                                    action.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
                if (pair.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
