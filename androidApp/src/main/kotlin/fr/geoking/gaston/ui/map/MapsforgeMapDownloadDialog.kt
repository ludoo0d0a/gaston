package fr.geoking.gaston.ui.map

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
import fr.geoking.gaston.auto.mapsforge.DownloadProgress
import fr.geoking.gaston.auto.mapsforge.MapsforgeMapManager
import fr.geoking.gaston.auto.mapsforge.MapsforgePresetServers
import fr.geoking.gaston.auto.mapsforge.MapsforgeServerMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
@Composable
fun MapsforgeMapDownloadDialog(
    mapManager: MapsforgeMapManager,
    onDismiss: () -> Unit,
    onMapFileChanged: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val installedMaps by mapManager.installedMaps.collectAsState()
    val downloadProgress by mapManager.downloadProgress.collectAsState()
    val activeMap = remember(installedMaps) { mapManager.getActiveMapFile() }

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
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.mapsforge_offline_maps_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        text = "${stringResource(R.string.mapsforge_download_error)}: ${progress.error}",
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
                        val isActive = activeMap?.name == file.name
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                RadioButton(
                                    selected = isActive,
                                    onClick = {
                                        mapManager.setActiveMapFile(file)
                                        mapManager.refreshInstalledMaps()
                                        onMapFileChanged()
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
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
                                    contentDescription = stringResource(R.string.settings_clear_logs),
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
                    item {
                        Text(
                            text = stringResource(R.string.mapsforge_section_france_regions),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(MapsforgePresetServers.FRANCE_REGION_MAPS) { preset ->
                        MapsforgePresetDownloadRow(
                            preset = preset,
                            installedMaps = installedMaps,
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
                    items(MapsforgePresetServers.NEIGHBOR_MAPS) { preset ->
                        MapsforgePresetDownloadRow(
                            preset = preset,
                            installedMaps = installedMaps,
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
                            installedMaps = installedMaps,
                            progress = progress,
                            scope = scope,
                            mapManager = mapManager,
                            onMapFileChanged = onMapFileChanged,
                        )
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
private fun MapsforgePresetDownloadRow(
    preset: MapsforgeServerMap,
    installedMaps: List<File>,
    progress: DownloadProgress?,
    scope: CoroutineScope,
    mapManager: MapsforgeMapManager,
    onMapFileChanged: () -> Unit,
) {
    val isInstalled = installedMaps.any {
        it.name.equals(preset.name, ignoreCase = true) ||
            it.name.equals("${preset.name}.map", ignoreCase = true) ||
            it.name.contains(preset.name, ignoreCase = true)
    }
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
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
                onClick = {
                    scope.launch {
                        val res = mapManager.downloadMap(preset.url, "${preset.name}.map")
                        if (res.isSuccess) {
                            onMapFileChanged()
                        }
                    }
                },
                enabled = progress == null || progress.isComplete || progress.error != null
            ) {
                Text(if (isInstalled) stringResource(R.string.action_refresh) else stringResource(R.string.action_download))
            }
        }
    }
}
