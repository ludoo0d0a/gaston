package fr.geoking.gaston.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.gaston.*
import fr.geoking.gaston.poi.PoiProviderType
import fr.geoking.gaston.poi.anyProvidesElectric
import fr.geoking.gaston.poi.anyProvidesFuel
import fr.geoking.gaston.ui.*

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

    // Mode: 0 for Fuel, 1 for Electric, 2 for Hybrid
    val filterMode = remember(settings) {
        when (settings.effectiveEnergyFilterMode()) {
            EnergyFilterMode.Fuel -> 0
            EnergyFilterMode.Electric -> 1
            EnergyFilterMode.Hybrid -> 2
        }
    }

    val filterBarProviders = remember(settings, mapCenterLatitude, mapCenterLongitude) {
        when {
            mapCenterLatitude != null && mapCenterLongitude != null ->
                settings.effectiveProvidersAt(mapCenterLatitude, mapCenterLongitude)
            else -> settings.effectiveProviders()
        }
    }

    val effectiveEnergyIds = settings.effectiveMapEnergyFilterIds()
    val effectivePowerLevels = settings.effectiveIrvePowerLevels()
    val effectiveBrands = settings.effectiveFuelBrandFilterIds()
    val effectiveOperators = settings.effectiveIrveOperatorFilter()

    val favoritesFilterActive = favoritesFilterEnabled && showFavoritesOnly
    val activeFilterCount = remember(settings, filterMode, favoritesFilterActive, effectiveEnergyIds, effectivePowerLevels, effectiveBrands, effectiveOperators) {
        val favCount = if (favoritesFilterActive) 1 else 0
        val amenityCount = if (settings.selectedOverpassAmenityTypes.isNotEmpty()) 1 else 0

        if (settings.useVehicleFilter) return@remember 1 + favCount + amenityCount

        val fuelFilters = if (filterMode == 0 || filterMode == 2) {
            val brandFilter = if (effectiveBrands.isNotEmpty()) 1 else 0
            val energyFilter = if (effectiveEnergyIds.any { it != "electric" }) 1 else 0
            brandFilter + energyFilter
        } else 0

        val elecFilters = if (filterMode == 1 || filterMode == 2) {
            val operatorFilter = if (effectiveOperators.isNotEmpty()) 1 else 0
            val powerFilter = if (effectivePowerLevels.isNotEmpty()) 1 else 0
            val connectorFilter = if (settings.selectedMapConnectorTypes.isNotEmpty()) 1 else 0
            operatorFilter + powerFilter + connectorFilter
        } else 0

        favCount + amenityCount + fuelFilters + elecFilters
    }

    ExtendedFloatingActionButton(
        onClick = { showSheet = true },
        icon = { Icon(Icons.Default.FilterList, contentDescription = null) },
        text = {
            Text(
                if (activeFilterCount > 0) "Filters ($activeFilterCount)" else "Filters"
            )
        },
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
    )

    if (showSheet) {
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
                        "Search Filters",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("For my car", color = Color.White, style = MaterialTheme.typography.bodyMedium)
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

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    SegmentedButton(
                        selected = filterMode == 0,
                        onClick = {
                            if (filterMode != 0) {
                                settingsManager.setEnergyFilterMode(EnergyFilterMode.Fuel)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        label = { Text("Fuel") },
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                            inactiveContainerColor = Color(0xFF334155),
                            inactiveContentColor = Color.White
                        )
                    )
                    SegmentedButton(
                        selected = filterMode == 1,
                        onClick = {
                            if (filterMode != 1) {
                                settingsManager.setEnergyFilterMode(EnergyFilterMode.Electric)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        label = { Text("Electric") },
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                            inactiveContainerColor = Color(0xFF334155),
                            inactiveContentColor = Color.White
                        )
                    )
                    SegmentedButton(
                        selected = filterMode == 2,
                        onClick = {
                            if (filterMode != 2) {
                                settingsManager.setEnergyFilterMode(EnergyFilterMode.Hybrid)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        label = { Text("Hybrid") },
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                            inactiveContainerColor = Color(0xFF334155),
                            inactiveContentColor = Color.White
                        )
                    )
                }

                if (favoritesFilterEnabled && onShowFavoritesOnlyChange != null) {
                    FilterSectionTitle("Favorites")
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = showFavoritesOnly,
                            onClick = { onShowFavoritesOnlyChange(!showFavoritesOnly) },
                            label = { Text("My favorites") },
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
                            "electric" -> "Electric"
                            "hybrid" -> "Gas & Electric"
                            else -> "Fuel"
                        }
                        Text(
                            "Vehicle filters active: $energyLabel\n" +
                            "Using your car's predefined preferences.",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                } else {
                    if (filterMode == 0 || filterMode == 2) {
                        FuelFilters(settingsManager, filterBarProviders)
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                    if (filterMode == 1 || filterMode == 2) {
                        ElectricFilters(settingsManager, filterBarProviders)
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                AmenityFilters(settingsManager)

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { showSheet = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apply")
                }
            }
        }
    }
}

@Composable
private fun AmenityFilters(settingsManager: SettingsManager) {
    val settings by settingsManager.settings.collectAsState()

    FilterSectionTitle("Amenities (Overpass)")
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OVERPASS_AMENITY_OPTIONS.forEach { (id, label) ->
            val isSelected = settings.selectedOverpassAmenityTypes.contains(id)
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
                label = { Text(label) },
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
    val brandOptions = remember { BrandHelper.getGasBrands() }

    if (providers.anyProvidesFuel()) {
        val effectiveEnergyIds = settings.effectiveMapEnergyFilterIds()
        FilterSectionTitle("Fuel Types")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MAP_ENERGY_OPTIONS.filter { it.first != "electric" }.forEach { (id, label) ->
                FuelFilterChip(
                    id = id,
                    label = label,
                    isSelected = effectiveEnergyIds.contains(id),
                    onClick = {
                        val current = settings.selectedMapEnergyTypes
                        val next = if (current.contains(id)) current - id else current + id
                        settingsManager.setUseVehicleFilter(false)
                        settingsManager.setMapEnergyTypes(next)
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    FilterSectionTitle("Brands")
    MultiSelectBrandFilter(
        options = brandOptions,
        selectedIds = settings.effectiveFuelBrandFilterIds(),
        onUpdate = { settingsManager.setMapBrands(it) }
    )
}

@Composable
private fun ElectricFilters(settingsManager: SettingsManager, providers: Set<PoiProviderType>) {
    val settings by settingsManager.settings.collectAsState()
    val brandOptions = remember { BrandHelper.getElectricBrands() }

    if (providers.anyProvidesElectric()) {
        val effectivePowerLevels = settings.effectiveIrvePowerLevels()
        FilterSectionTitle("Power Range")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MAP_IRVE_POWER_OPTIONS.forEach { (kw, label) ->
                PowerFilterChip(
                    kw = kw,
                    label = label,
                    isSelected = effectivePowerLevels.contains(kw),
                    onClick = {
                        val current = settings.mapPowerLevels
                        val next = if (current.contains(kw)) current - kw else current + kw
                        settingsManager.setUseVehicleFilter(false)
                        settingsManager.setMapPowerLevels(next)
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    FilterSectionTitle("Connectors")
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

    FilterSectionTitle("Brands / Operators")
    MultiSelectBrandFilter(
        options = brandOptions,
        selectedIds = settings.effectiveIrveOperatorFilter(),
        onUpdate = { settingsManager.setMapIrveOperators(it) },
        label = "Search operators..."
    )
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MultiSelectBrandFilter(
    options: List<Pair<String, String>>,
    selectedIds: Set<String>,
    onUpdate: (Set<String>) -> Unit,
    label: String = "Search brands..."
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredOptions = remember(searchQuery, options) {
        if (searchQuery.isBlank()) options
        else options.filter { it.second.contains(searchQuery, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(label, color = Color.White.copy(alpha = 0.7f)) },
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
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                    .fillMaxWidth()
            )

            if (filteredOptions.isNotEmpty()) {
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    containerColor = Color(0xFF334155)
                ) {
                    filteredOptions.forEach { (id, brandName) ->
                        val isSelected = selectedIds.contains(id)
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = null,
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = MaterialTheme.colorScheme.primary,
                                            uncheckedColor = Color.White.copy(alpha = 0.5f)
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(brandName, color = Color.White)
                                }
                            },
                            onClick = {
                                val next = if (isSelected) selectedIds - id else selectedIds + id
                                onUpdate(next)
                            }
                        )
                    }
                }
            }
        }

        if (selectedIds.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                selectedIds.forEach { id ->
                    val brandName = options.find { it.first == id }?.second ?: id
                    FilterChip(
                        selected = true,
                        onClick = { onUpdate(selectedIds - id) },
                        label = { Text(brandName, fontSize = 12.sp) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }
    }
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
