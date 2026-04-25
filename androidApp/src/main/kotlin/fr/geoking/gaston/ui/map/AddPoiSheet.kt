package fr.geoking.gaston.ui.map

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.geoking.gaston.community.CommunityPoiRepository
import fr.geoking.gaston.community.communityPoiId
import fr.geoking.gaston.feature.location.LocationHelper
import fr.geoking.gaston.poi.Poi
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPoiSheet(
    initialLat: Double?,
    initialLng: Double?,
    linkedOfficialId: String?,
    existingCommunityId: String?,
    initialName: String,
    initialAddress: String,
    communityRepo: CommunityPoiRepository?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var address by remember(initialAddress) { mutableStateOf(initialAddress) }
    var isElectric by remember { mutableStateOf(false) }
    var powerKw by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    existingCommunityId != null -> "Edit POI"
                    linkedOfficialId != null -> "Suggest correction"
                    else -> "Add POI"
                },
                color = Color.White
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name", color = Color.White.copy(alpha = 0.8f)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
                    )
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address", color = Color.White.copy(alpha = 0.8f)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text("Type", color = Color.White.copy(alpha = 0.9f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !isElectric,
                            onClick = { isElectric = false },
                            label = { Text("Gas") }
                        )
                        FilterChip(
                            selected = isElectric,
                            onClick = { isElectric = true },
                            label = { Text("IRVE") }
                        )
                    }
                }
                if (isElectric) {
                    OutlinedTextField(
                        value = powerKw,
                        onValueChange = { powerKw = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Power (kW)", color = Color.White.copy(alpha = 0.8f)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White.copy(alpha = 0.5f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (communityRepo == null) {
                        onDismiss()
                        return@TextButton
                    }
                    scope.launch {
                        val lat = initialLat ?: LocationHelper.getCurrentLocation(context)?.latitude
                        val lng = initialLng ?: LocationHelper.getCurrentLocation(context)?.longitude
                        if (lat != null && lng != null && name.isNotBlank()) {
                            val id = existingCommunityId ?: communityPoiId()
                            val poi = Poi(
                                id = id,
                                name = name.trim(),
                                address = address.trim().ifBlank { "%.4f, %.4f".format(lat, lng) },
                                latitude = lat,
                                longitude = lng,
                                isElectric = isElectric,
                                powerKw = powerKw.toDoubleOrNull()
                            )
                            if (existingCommunityId != null) {
                                communityRepo.updateCommunityPoi(existingCommunityId, poi)
                            } else {
                                communityRepo.addCommunityPoi(poi, linkedOfficialId)
                            }
                            onSaved()
                        }
                        onDismiss()
                    }
                }
            ) {
                Text("Save", color = Color(0xFF22C55E))
            }
        },
        dismissButton = {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        },
        containerColor = Color(0xFF1E293B)
    )
}
