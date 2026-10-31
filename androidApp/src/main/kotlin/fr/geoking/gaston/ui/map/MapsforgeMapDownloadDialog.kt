package fr.geoking.gaston.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.geoking.gaston.R
import fr.geoking.gaston.auto.map.mapsforge.DownloadProgress
import fr.geoking.gaston.auto.map.mapsforge.MapsforgeMapManager
import fr.geoking.gaston.auto.map.mapsforge.MapsforgePresetServers
import fr.geoking.gaston.auto.map.mapsforge.MapsforgeServerMap
import fr.geoking.gaston.ui.map.preview.MapRegionPreviewMap
import fr.geoking.gaston.ui.map.preview.toRegionItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun MapsforgeMapDownloadDialog(
    mapManager: MapsforgeMapManager,
    onDismiss: () -> Unit,
    onMapFileChanged: () -> Unit = {},
    initialQuery: String = "",
) {
    val scope = rememberCoroutineScope()
    val installedMaps by mapManager.installedMaps.collectAsState()
    val downloadProgress by mapManager.downloadProgress.collectAsState()
    val activeMap = remember(installedMaps) { mapManager.getActiveMapFile() }
    var searchQuery by remember { mutableStateOf(initialQuery) }
    var selectedRegionKey by remember { mutableStateOf<String?>(null) }
    val filteredPresets = remember(searchQuery) {
        MapsforgePresetServers.filterPresets(searchQuery)
    }
    val allPresets = remember { MapsforgePresetServers.PRESET_MAPS }
    val regionItems = remember(allPresets) { allPresets.map { it.toRegionItem() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.mapsforge_offline_maps),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.mapsforge_offline_maps_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                MapRegionPreviewMap(
                    regions = regionItems,
                    selectedRegionKey = selectedRegionKey,
                    onRegionSelected = { selected ->
                        selectedRegionKey = selected.key
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.offline_maps_search_hint)) },
                )

                val progress = downloadProgress
                if (progress != null && !progress.isComplete && progress.error == null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.mapsforge_downloading, progress.fileName),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { if (progress.totalBytes > 0) progress.bytesDownloaded.toFloat() / progress.totalBytes else 0f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${progress.progressPercent}% (${progress.bytesDownloaded / (1024 * 1024)} MB)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                } else if (progress?.error != null) {
                    Text(
                        text = stringResource(R.string.mapsforge_download_error) + ": ${progress.error}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (installedMaps.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.mapsforge_active_map),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    installedMaps.forEach { file ->
                        val isActive = activeMap?.absolutePath == file.absolutePath
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = isActive,
                                    onClick = {
                                        mapManager.setActiveMapFile(file)
                                        onMapFileChanged()
                                    }
                                )
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    mapManager.deleteMap(file)
                                    onMapFileChanged()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.action_remove),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (searchQuery.isNotBlank()) {
                        if (filteredPresets.isEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.offline_maps_search_empty, searchQuery.trim()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            items(
                                items = filteredPresets,
                                key = { "${it.storageKey}-${it.name}" },
                                contentType = { "mapsforge_preset" },
                            ) { preset ->
                                MapsforgePresetDownloadRow(
                                    preset = preset,
                                    isSelected = selectedRegionKey == preset.storageKey,
                                    onSelectRegion = { selectedRegionKey = preset.storageKey },
                                    progress = progress,
                                    scope = scope,
                                    mapManager = mapManager,
                                    onMapFileChanged = onMapFileChanged,
                                )
                            }
                        }
                    } else {
                        item {
                            Text(
                                text = stringResource(R.string.mapsforge_section_france_regions),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        items(
                            items = MapsforgePresetServers.FRANCE_REGION_MAPS,
                            key = { "fr-${it.storageKey}-${it.name}" },
                            contentType = { "mapsforge_preset" },
                        ) { preset ->
                            MapsforgePresetDownloadRow(
                                preset = preset,
                                isSelected = selectedRegionKey == preset.storageKey,
                                onSelectRegion = { selectedRegionKey = preset.storageKey },
                                progress = progress,
                                scope = scope,
                                mapManager = mapManager,
                                onMapFileChanged = onMapFileChanged,
                            )
                        }
                        item {
                            Text(
                                text = stringResource(R.string.mapsforge_section_neighbors),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(
                            items = MapsforgePresetServers.NEIGHBOR_MAPS,
                            key = { "nb-${it.storageKey}-${it.name}" },
                            contentType = { "mapsforge_preset" },
                        ) { preset ->
                            MapsforgePresetDownloadRow(
                                preset = preset,
                                isSelected = selectedRegionKey == preset.storageKey,
                                onSelectRegion = { selectedRegionKey = preset.storageKey },
                                progress = progress,
                                scope = scope,
                                mapManager = mapManager,
                                onMapFileChanged = onMapFileChanged,
                            )
                        }
                        item {
                            Text(
                                text = stringResource(R.string.mapsforge_section_country_wide),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        item {
                            MapsforgePresetDownloadRow(
                                preset = MapsforgePresetServers.FRANCE_ALL,
                                isSelected = selectedRegionKey == MapsforgePresetServers.FRANCE_ALL.storageKey,
                                onSelectRegion = { selectedRegionKey = MapsforgePresetServers.FRANCE_ALL.storageKey },
                                progress = progress,
                                scope = scope,
                                mapManager = mapManager,
                                onMapFileChanged = onMapFileChanged,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

@Composable
internal fun MapsforgePresetDownloadRow(
    preset: MapsforgeServerMap,
    isSelected: Boolean = false,
    onSelectRegion: () -> Unit = {},
    progress: DownloadProgress?,
    scope: CoroutineScope,
    mapManager: MapsforgeMapManager,
    onMapFileChanged: () -> Unit,
) {
    val installedMaps by mapManager.installedMaps.collectAsState()
    val isInstalled = remember(installedMaps, preset) { mapManager.isPresetInstalled(preset) }
    var showWarningDialog by remember { mutableStateOf(false) }

    if (showWarningDialog) {
        AlertDialog(
            onDismissRequest = { showWarningDialog = false },
            title = { Text(stringResource(R.string.map_download_warning_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.map_download_warning_message,
                        preset.name,
                        preset.sizeEstimateMb
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWarningDialog = false
                        scope.launch {
                            val res = mapManager.downloadMap(preset.url, "${preset.storageKey}.map")
                            if (res.isSuccess) {
                                onMapFileChanged()
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_download))
                }
            },
            dismissButton = {
                TextButton(onClick = { showWarningDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectRegion() },
        shape = RoundedCornerShape(8.dp),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            CardDefaults.outlinedCardBorder()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${preset.region} · " + if (isInstalled) {
                        stringResource(R.string.mapsforge_installed, preset.sizeEstimateMb)
                    } else {
                        stringResource(R.string.mapsforge_available, preset.sizeEstimateMb)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = { showWarningDialog = true },
                enabled = progress == null || progress.isComplete || progress.error != null
            ) {
                Text(if (isInstalled) stringResource(R.string.action_refresh) else stringResource(R.string.action_download))
            }
        }
    }
}
