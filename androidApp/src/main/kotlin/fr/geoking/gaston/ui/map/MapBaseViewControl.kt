package fr.geoking.gaston.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.geoking.gaston.MapBaseView
import fr.geoking.gaston.R

@Composable
fun MapBaseViewControl(
    current: MapBaseView,
    onSelect: (MapBaseView) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        SmallFloatingActionButton(
            onClick = { expanded = true },
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Icon(
                imageVector = Icons.Default.Layers,
                contentDescription = stringResource(R.string.action_map_view),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            MapBaseView.entries.forEach { view ->
                DropdownMenuItem(
                    text = { Text(view.label()) },
                    onClick = {
                        expanded = false
                        onSelect(view)
                    },
                    leadingIcon = {
                        RadioButton(
                            selected = current == view,
                            onClick = {
                                expanded = false
                                onSelect(view)
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun MapBaseView.label(): String = stringResource(
    when (this) {
        MapBaseView.Streets -> R.string.map_view_streets
        MapBaseView.Satellite -> R.string.map_view_satellite
        MapBaseView.Hybrid -> R.string.map_view_hybrid
        MapBaseView.Terrain -> R.string.map_view_terrain
    }
)
