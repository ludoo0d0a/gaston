package fr.geoking.gaston.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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

@Composable
fun FuelFilterChip(
    id: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color = ColorHelper.getFuelColor(id) ?: MaterialTheme.colorScheme.primary
    FilterChip(
        selected = isSelected,
        onClick = onClick,
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

@Composable
fun PowerFilterChip(
    kw: Int,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color = ColorHelper.getPowerColorByLevel(kw)
    FilterChip(
        selected = isSelected,
        onClick = onClick,
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

fun LazyListScope.energySelectorItems(
    settings: AppSettings,
    settingsManager: SettingsManager,
    providers: Set<PoiProviderType>
) {
    if (providers.anyProvidesFuel()) {
        items(MAP_ENERGY_OPTIONS.filter { it.first != "electric" }) { (id, label) ->
            FuelFilterChip(
                id = id,
                label = label,
                isSelected = settings.effectiveMapEnergyFilterIds().contains(id),
                onClick = {
                    val current = settings.selectedMapEnergyTypes
                    val next = if (current.contains(id)) current - id else current + id
                    settingsManager.setUseVehicleFilter(false)
                    settingsManager.setMapEnergyTypes(next)
                }
            )
        }
    }

    if (providers.anyProvidesElectric()) {
        items(MAP_IRVE_POWER_OPTIONS) { (kw, label) ->
            PowerFilterChip(
                kw = kw,
                label = label,
                isSelected = settings.effectiveIrvePowerLevels().contains(kw),
                onClick = {
                    val current = settings.mapPowerLevels
                    val next = if (current.contains(kw)) current - kw else current + kw
                    settingsManager.setUseVehicleFilter(false)
                    settingsManager.setMapPowerLevels(next)
                }
            )
        }
    }
}
