package fr.geoking.gaston.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.geoking.gaston.R
import fr.geoking.gaston.*
import fr.geoking.gaston.poi.PoiProviderType
import fr.geoking.gaston.poi.anyProvidesElectric
import fr.geoking.gaston.poi.anyProvidesFuel
import fr.geoking.gaston.ui.*
import fr.geoking.gaston.ui.map.AmenityIconCatalog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterFab(
    settingsManager: SettingsManager,
    modifier: Modifier = Modifier,
    /** When set, the sheet shows the country/region for this map position (same as auto provider area). */
    mapCenterLatitude: Double? = null,
    mapCenterLongitude: Double? = null,
    favoritesFilterEnabled: Boolean = false,
    showFavoritesOnly: Boolean = false,
    onShowFavoritesOnlyChange: ((Boolean) -> Unit)? = null
) {
    val settings by settingsManager.settings.collectAsState()
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    val currentSearchMode = rememberSearchMode(settings)

    val filterBarProviders = remember(settings, mapCenterLatitude, mapCenterLongitude) {
        when {
            mapCenterLatitude != null && mapCenterLongitude != null ->
                settings.effectiveProvidersAt(mapCenterLatitude, mapCenterLongitude)
            else -> settings.effectiveProviders()
        }
    }

    val effectiveEnergyIds = settings.effectiveMapEnergyFilterIds()
    val effectivePowerLevels = settings.effectiveIrvePowerLevels()

    val favoritesFilterActive = favoritesFilterEnabled && showFavoritesOnly
    val activeFilterCount = remember(settings, currentSearchMode, favoritesFilterActive, effectiveEnergyIds, effectivePowerLevels) {
        val favCount = if (favoritesFilterActive) 1 else 0
        val amenityCount = if (settings.selectedOverpassAmenityTypes.isNotEmpty()) 1 else 0

        if (settings.useVehicleFilter) return@remember 1 + favCount + amenityCount

        val fuelFilters = if (currentSearchMode == SearchMode.Fuel) {
            val energyFilter = if (effectiveEnergyIds.any { it != "electric" }) 1 else 0
            energyFilter
        } else 0

        val elecFilters = if (currentSearchMode == SearchMode.EV) {
            val powerFilter = if (effectivePowerLevels.isNotEmpty()) 1 else 0
            val connectorFilter = if (settings.selectedMapConnectorTypes.isNotEmpty()) 1 else 0
            powerFilter + connectorFilter
        } else 0

        favCount + amenityCount + fuelFilters + elecFilters
    }

    ExtendedFloatingActionButton(
        onClick = { showSheet = true },
        icon = { Icon(Icons.Default.FilterList, contentDescription = null) },
        text = {
            Text(
                if (activeFilterCount > 0) stringResource(R.string.filters_with_count, activeFilterCount) else stringResource(R.string.filters)
            )
        },
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
    )

    if (showSheet) {
        // Brand/operator filters are deprecated on the phone map filter sheet.
        // Ensure we don't keep silently filtering if the user had old values stored.
        LaunchedEffect(Unit) {
            if (settings.mapBrands.isNotEmpty()) settingsManager.setMapBrands(emptySet())
            if (settings.mapIrveOperators.isNotEmpty()) settingsManager.setMapIrveOperators(emptySet())
        }

        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            sheetGesturesEnabled = true,
            containerColor = Color(0xFF1E293B),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.7f)) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.search_filters),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.for_my_car), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = settings.useVehicleFilter,
                            onCheckedChange = { settingsManager.setUseVehicleFilter(it) },
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                if (mapCenterLatitude != null && mapCenterLongitude != null) {
                    Text(
                        text = "Area: ${countryDisplayLabelAtMapPosition(mapCenterLatitude, mapCenterLongitude)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    )
                }

                SearchModeSelector(
                    currentMode = currentSearchMode,
                    settingsManager = settingsManager,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                if (favoritesFilterEnabled && onShowFavoritesOnlyChange != null) {
                    FilterSectionTitle(stringResource(R.string.filter_section_favorites))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = showFavoritesOnly,
                            onClick = { onShowFavoritesOnlyChange(!showFavoritesOnly) },
                            label = { Text(stringResource(R.string.filter_chip_my_favorites)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Star,
                                    null,
                                    Modifier.size(18.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                labelColor = Color.White,
                                containerColor = Color(0xFF334155)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = showFavoritesOnly,
                                borderColor = Color.White.copy(alpha = 0.3f),
                                selectedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                if (settings.useVehicleFilter) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                        val energyLabel = when (settings.vehicleEnergy) {
                            "electric" -> stringResource(R.string.vehicle_energy_electric)
                            "hybrid" -> stringResource(R.string.vehicle_energy_hybrid)
                            else -> stringResource(R.string.vehicle_energy_fuel)
                        }
                        Text(
                            stringResource(R.string.vehicle_filters_active, energyLabel),
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                } else {
                    if (currentSearchMode == SearchMode.Fuel) {
                        FuelFilters(settingsManager, filterBarProviders)
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                    if (currentSearchMode == SearchMode.EV) {
                        ElectricFilters(settingsManager, filterBarProviders)
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                AmenityFilters(settingsManager)
            }
        }
    }
}

@Composable
private fun AmenityFilters(settingsManager: SettingsManager) {
    val settings by settingsManager.settings.collectAsState()

    FilterSectionTitle(stringResource(R.string.filter_section_amenities))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OVERPASS_AMENITY_OPTIONS.forEach { (id, resId) ->
            val isSelected = settings.selectedOverpassAmenityTypes.contains(id)
            val icon = remember(id) { AmenityIconCatalog.iconForOsmId(id) }
            FilterChip(
                selected = isSelected,
                onClick = {
                    val next = if (isSelected) {
                        settings.selectedOverpassAmenityTypes - id
                    } else {
                        settings.selectedOverpassAmenityTypes + id
                    }
                    settingsManager.setOverpassAmenityTypes(next)
                },
                label = { Text(stringResource(resId)) },
                leadingIcon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    labelColor = Color.White,
                    containerColor = Color(0xFF334155)
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = Color.White.copy(alpha = 0.3f),
                    selectedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun FuelFilters(settingsManager: SettingsManager, providers: Set<PoiProviderType>) {
    val settings by settingsManager.settings.collectAsState()

    if (providers.anyProvidesFuel()) {
        val selectedEnergyIds = settings.selectedMapEnergyTypes
        FilterSectionTitle(stringResource(R.string.filter_section_fuel_types))
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
                        settingsManager.setUseVehicleFilter(false)
                        settingsManager.setMapEnergyTypes(setOf(id))
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ElectricFilters(settingsManager: SettingsManager, providers: Set<PoiProviderType>) {
    val settings by settingsManager.settings.collectAsState()

    if (providers.anyProvidesElectric()) {
        val effectivePowerLevels = settings.effectiveIrvePowerLevels()
        FilterSectionTitle(stringResource(R.string.filter_section_power_range))
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
                        settingsManager.setUseVehicleFilter(false)
                        settingsManager.setMapPowerLevels(next)
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    FilterSectionTitle(stringResource(R.string.filter_section_connectors))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MAP_CONNECTOR_OPTIONS.forEach { (id, label) ->
            FilterChip(
                selected = settings.selectedMapConnectorTypes.contains(id),
                onClick = {
                    val newTypes = if (settings.selectedMapConnectorTypes.contains(id)) settings.selectedMapConnectorTypes - id else settings.selectedMapConnectorTypes + id
                    settingsManager.setMapConnectorTypes(newTypes)
                },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    labelColor = Color.White,
                    containerColor = Color(0xFF334155)
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = settings.selectedMapConnectorTypes.contains(id),
                    borderColor = Color.White.copy(alpha = 0.3f),
                    selectedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun FilterSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = Color.White.copy(alpha = 0.7f),
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactDropdownField(
    options: List<Pair<*, String>>,
    selectedOption: Any?,
    onOptionSelected: (Any?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.first == selectedOption }?.second ?: "Select..."

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                focusedTrailingIconColor = Color.White,
                unfocusedTrailingIconColor = Color.White.copy(alpha = 0.7f)
            ),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Color(0xFF334155)
        ) {
            options.forEach { (option, label) ->
                DropdownMenuItem(
                    text = { Text(label, color = Color.White) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
