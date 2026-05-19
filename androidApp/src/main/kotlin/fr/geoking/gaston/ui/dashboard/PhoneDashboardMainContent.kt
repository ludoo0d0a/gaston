package fr.geoking.gaston.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.geoking.gaston.AppSettings
import fr.geoking.gaston.BuildConfig
import fr.geoking.gaston.PoiProviderSelectionMode
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.api.geocoding.GeocodedPlace
import fr.geoking.gaston.api.geocoding.GeocodingClient
import fr.geoking.gaston.intent.NavDestination
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiProviderType
import fr.geoking.gaston.repository.FuelForecastRepository
import fr.geoking.gaston.repository.FuelForecastUiState
import fr.geoking.gaston.ui.SettingsScreenPage
import fr.geoking.gaston.ui.components.*

private typealias DashboardMode = SearchMode
private typealias DashboardRow = SearchRow

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
    val currentMode = rememberSearchMode(settings)

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        item {
            SearchModeSelector(
                currentMode = currentMode,
                settingsManager = settingsManager
            )
        }

        item {
            SearchCategorySelector(
                currentMode = currentMode,
                settings = settings,
                settingsManager = settingsManager,
                onOpenSettings = onOpenSettings
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
                currentMode = currentMode,
                settings = settings,
                onPoiSelected = onPoiSelected,
                onOpenMap = onOpenMap
            )
        }

        item {
            val gridActions = listOf(
                DashboardRow(
                    title = "Itinéraire",
                    subtitle = "Routes",
                    iconResId = R.drawable.ic_directions,
                    onClick = { onOpenRoutes(null) },
                    enabled = mapDepsReady
                ),
                DashboardRow(
                    title = "Réseau",
                    subtitle = "Network",
                    iconResId = R.drawable.ic_signal_cellular,
                    onClick = onOpenNetworkDiagnostics
                )
            )
            PhoneDashboardOtherActionsGrid(otherActions = gridActions)
        }

        if (fuelForecastRepository != null && currentMode == DashboardMode.Fuel) {
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
    currentMode: DashboardMode,
    settings: AppSettings,
    onPoiSelected: (Poi) -> Unit,
    onOpenMap: (Poi?) -> Unit
) {
    val title = when (currentMode) {
        DashboardMode.Fuel -> if (selectedSearchLocation != null) "Cheapest near ${cityLabelFromGeocodedPlace(selectedSearchLocation)}" else "Cheapest nearby"
        DashboardMode.EV -> "Nearest stations"
        DashboardMode.MyCar -> {
            if (settings.vehicleEnergy == "gas") "Cheapest nearby" else "Nearest stations"
        }
        DashboardMode.Other -> {
            if (settings.selectedOverpassAmenityTypes.contains("parking")) "Nearest parkings" else "Nearest nearby"
        }
    }

    if (isLoadingPois && showLoaderByDelay && nearbyPois.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { onOpenMap(null) },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_map),
                            contentDescription = "Open Map",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Searching nearby...",
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
            title = title
        )
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
                painter = painterResource(R.drawable.ic_sos),
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
                    painter = painterResource(R.drawable.ic_poi_gas),
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        otherActions.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pair.forEach { action ->
                    Card(
                        onClick = action.onClick,
                        enabled = action.enabled,
                        modifier = Modifier.weight(1f).height(100.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (action.iconResId != null) {
                                Icon(
                                    painter = painterResource(action.iconResId),
                                    contentDescription = null,
                                    tint = if (action.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                    modifier = Modifier.size(28.dp)
                                )
                            } else if (action.icon != null) {
                                Icon(
                                    imageVector = action.icon,
                                    contentDescription = null,
                                    tint = if (action.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = action.title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (action.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    }
                }
                if (pair.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
