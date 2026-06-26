package fr.geoking.gaston.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import fr.geoking.gaston.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.geoking.gaston.AppSettings
import fr.geoking.gaston.PoiProviderSelectionMode
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.effectiveEnergyFilterMode
import fr.geoking.gaston.effectiveIrvePowerLevels
import fr.geoking.gaston.poi.EnergyFilterMode
import fr.geoking.gaston.poi.PoiProviderType
import fr.geoking.gaston.ui.*
import fr.geoking.gaston.ui.map.AmenityIconCatalog

enum class SearchMode { Fuel, EV, MyVehicle, Other }

data class SearchRow(
    val title: String,
    val subtitle: String,
    val icon: ImageVector? = null,
    val iconResId: Int? = null,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val mode: SearchMode? = null
)

@Composable
fun rememberSearchMode(settings: AppSettings): SearchMode {
    return remember(settings) {
        val isOtherSelected = settings.poiProviderSelectionMode == PoiProviderSelectionMode.Manual &&
                settings.selectedPoiProviders == setOf(PoiProviderType.Overpass)

        when {
            isOtherSelected -> SearchMode.Other
            settings.useVehicleFilter -> SearchMode.MyVehicle
            settings.effectiveEnergyFilterMode() == EnergyFilterMode.Electric -> SearchMode.EV
            else -> SearchMode.Fuel
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchModeSelector(
    currentMode: SearchMode,
    settingsManager: SettingsManager,
    modifier: Modifier = Modifier
) {
    val quickActions = listOf(
        SearchRow(
            title = stringResource(R.string.search_mode_fuel),
            subtitle = stringResource(R.string.search_mode_fuel),
            iconResId = R.drawable.ic_poi_gas,
            mode = SearchMode.Fuel,
            onClick = { settingsManager.setEnergyFilterMode(EnergyFilterMode.Fuel) }
        ),
        SearchRow(
            title = stringResource(R.string.search_mode_ev),
            subtitle = stringResource(R.string.search_mode_ev),
            iconResId = R.drawable.ic_poi_electric,
            mode = SearchMode.EV,
            onClick = { settingsManager.setEnergyFilterMode(EnergyFilterMode.Electric) }
        ),
        SearchRow(
            title = stringResource(R.string.search_mode_my_car),
            subtitle = stringResource(R.string.search_mode_my_car),
            iconResId = R.drawable.ic_directions_car,
            mode = SearchMode.MyVehicle,
            onClick = { settingsManager.setMyVehicleMode() }
        ),
        SearchRow(
            title = stringResource(R.string.search_mode_other),
            subtitle = stringResource(R.string.search_mode_other),
            iconResId = R.drawable.ic_category,
            mode = SearchMode.Other,
            onClick = { settingsManager.setOtherMode() }
        )
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            quickActions.forEach { action ->
                val isSelected = action.mode == currentMode
                val testTag = when (action.mode) {
                    SearchMode.Fuel -> "mode_fuel"
                    SearchMode.EV -> "mode_ev"
                    SearchMode.MyVehicle -> "mode_my_vehicle"
                    SearchMode.Other -> "mode_other"
                    else -> "mode_unknown"
                }
                Surface(
                    onClick = action.onClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag(testTag),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (action.iconResId != null) {
                            Icon(
                                painter = painterResource(action.iconResId),
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        } else if (action.icon != null) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = action.title,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchCategorySelector(
    currentMode: SearchMode,
    settings: AppSettings,
    settingsManager: SettingsManager,
    onOpenSettings: (List<SettingsScreenPage>?) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        when (currentMode) {
            SearchMode.Fuel -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val fuels = MAP_ENERGY_OPTIONS.filter { it.first != "electric" }
                    items(fuels.size) { index ->
                        val (id, label) = fuels[index]
                        FuelFilterChip(
                            id = id,
                            label = label,
                            isSelected = settings.selectedMapEnergyTypes.contains(id),
                            onClick = { settingsManager.setMapEnergyTypes(setOf(id)) }
                        )
                    }
                }
            }
            SearchMode.EV -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val isSwapSelected = settings.selectedMapEnergyTypes.contains("swap")
                    item {
                        FilterChip(
                            selected = isSwapSelected,
                            onClick = {
                                if (isSwapSelected) {
                                    settingsManager.saveSettings(settings.copy(selectedMapEnergyTypes = emptySet()))
                                } else {
                                    settingsManager.saveSettings(settings.copy(
                                        selectedMapEnergyTypes = setOf("swap"),
                                        mapPowerLevels = emptySet()
                                    ))
                                }
                            },
                            label = { Text(stringResource(R.string.filter_battery_swap)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.SwapHoriz,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                    if (!isSwapSelected) {
                        items(MAP_IRVE_POWER_OPTIONS.size) { index ->
                            val (kw, label) = MAP_IRVE_POWER_OPTIONS[index]
                            PowerFilterChip(
                                kw = kw,
                                label = label,
                                isSelected = settings.effectiveIrvePowerLevels().contains(kw),
                                onClick = {
                                    settingsManager.setMapPowerLevels(setOf(kw))
                                }
                            )
                        }
                    }
                }
            }
            SearchMode.MyVehicle -> {
                if (settings.vehicleBrand.isEmpty()) {
                    Card(
                        onClick = { onOpenSettings(listOf(SettingsScreenPage.VehicleConfig)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Row(
                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_directions_car),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.no_vehicle_profile),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    stringResource(R.string.tap_to_configure),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Icon(
                                painter = painterResource(R.drawable.ic_chevron_right),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    // Configured car: show a simple row with summary or settings access
                    FilterChip(
                        selected = true,
                        onClick = { onOpenSettings(listOf(SettingsScreenPage.VehicleConfig)) },
                        label = { Text("${settings.vehicleBrand} ${settings.vehicleModel}") },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_directions_car), null, Modifier.size(18.dp)) }
                    )
                }
            }
            SearchMode.Other -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Ensure parking is first
                    val sortedAmenities = OVERPASS_AMENITY_OPTIONS.sortedBy { if (it.first == "parking") 0 else 1 }
                    items(sortedAmenities.size) { index ->
                        val (id, resId) = sortedAmenities[index]
                        val isSelected = settings.selectedOverpassAmenityTypes.contains(id)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                val current = settings.selectedOverpassAmenityTypes
                                val next = if (isSelected) current - id else current + id
                                settingsManager.setOverpassAmenityTypes(next)
                            },
                            label = { Text(stringResource(resId)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = AmenityIconCatalog.iconForOsmId(id),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
