package fr.geoking.gaston.ui.parked

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.activeVehicle
import fr.geoking.gaston.feature.location.LocationHelper
import fr.geoking.gaston.intent.IntentNavigationHelper
import fr.geoking.gaston.parked.ParkCandidate
import fr.geoking.gaston.parked.ParkCandidateActions
import fr.geoking.gaston.parked.ParkCandidateSaveHelper
import fr.geoking.gaston.parked.ParkCandidateStore
import fr.geoking.gaston.parked.UndergroundParkingDetector
import fr.geoking.gaston.parkedPositionFor
import fr.geoking.gaston.parking.ParkingAggregator
import fr.geoking.gaston.parking.ParkingQueryContext
import fr.geoking.gaston.ui.dashboard.GastonTheme
import fr.geoking.gaston.vehicleById
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import java.text.DateFormat

private val ParkedAccent = Color(0xFF2E7D32)
private val ParkedOnAccent = Color.White

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ParkedCarFeatureScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit,
    initialVehicleId: String? = null,
    candidateLatitude: Double? = null,
    candidateLongitude: Double? = null,
) {
    val settings by settingsManager.settings.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val candidateStore = remember { GlobalContext.get().get<ParkCandidateStore>() }
    val parkActions = remember { GlobalContext.get().get<ParkCandidateActions>() }
    val storeCandidate by candidateStore.candidate.collectAsState()
    val userLocation by remember(context) {
        LocationHelper.getLocationUpdates(context)
    }.collectAsState(initial = null)

    var selectedVehicleId by remember(settings.activeVehicleId, settings.vehicles, initialVehicleId) {
        mutableStateOf(
            initialVehicleId?.takeIf { it.isNotBlank() }
                ?: settings.activeVehicleId.ifBlank { settings.vehicles.firstOrNull()?.id.orEmpty() }
                    .ifBlank { "default" }
        )
    }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var intentCandidateDismissed by remember { mutableStateOf(false) }
    var columnScrollEnabled by remember { mutableStateOf(true) }

    val vehicle = settings.vehicleById(selectedVehicleId) ?: settings.activeVehicle()
    val parked = settings.parkedPositionFor(selectedVehicleId)

    var isUnderground by remember(parked) { mutableStateOf(parked?.isUnderground ?: false) }
    var level by remember(parked) { mutableStateOf(parked?.level.orEmpty()) }
    var spotNumber by remember(parked) { mutableStateOf(parked?.spotNumber.orEmpty()) }
    var spotColor by remember(parked) { mutableStateOf(parked?.spotColor.orEmpty()) }
    var photoUri by remember(parked) { mutableStateOf(parked?.photoUri) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { photoUri = it.toString() }
    }

    val pendingCandidate: ParkCandidate? = when {
        !intentCandidateDismissed && candidateLatitude != null && candidateLongitude != null ->
            ParkCandidate(
                vehicleId = selectedVehicleId,
                latitude = candidateLatitude,
                longitude = candidateLongitude,
                createdAtEpochMs = System.currentTimeMillis(),
            )
        storeCandidate != null && storeCandidate!!.vehicleId == selectedVehicleId -> storeCandidate
        else -> null
    }

    val mapLat = parked?.latitude ?: pendingCandidate?.latitude
    val mapLon = parked?.longitude ?: pendingCandidate?.longitude

    LaunchedEffect(mapLat, mapLon, parked) {
        val lat = mapLat
        val lon = mapLon
        if (parked == null && lat != null && lon != null) {
            try {
                val aggregator = GlobalContext.get().getOrNull<ParkingAggregator>()
                val parkings = aggregator?.getParkingNearby(
                    ParkingQueryContext(userLat = lat, userLon = lon),
                    radiusMeters = 500,
                ).orEmpty()
                val isGpsLost = userLocation == null
                val detected = UndergroundParkingDetector.isLikelyUnderground(
                    latitude = lat,
                    longitude = lon,
                    nearbyParkings = parkings,
                    isGpsLostNearParking = isGpsLost,
                )
                if (detected) {
                    isUnderground = true
                }
            } catch (e: Exception) {
                // Ignore background detection errors
            }
        }
    }

    GastonTheme(themeMode = settings.uiThemeMode) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.parked_car_title),
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(R.drawable.ic_chevron_left),
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ParkedAccent,
                        titleContentColor = ParkedOnAccent,
                        navigationIconContentColor = ParkedOnAccent,
                    ),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState(), enabled = columnScrollEnabled),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.parked_car_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_auto_park_on_aa_disconnect_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(R.string.settings_auto_park_on_aa_disconnect_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.autoParkOnAaDisconnectEnabled,
                            onCheckedChange = { settingsManager.setAutoParkOnAaDisconnectEnabled(it) },
                            modifier = Modifier.testTag("parked_car_auto_aa_switch"),
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.parked_car_vehicle_section),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (settings.vehicles.isEmpty()) {
                    Text(
                        text = vehicle?.displayLabel()?.ifBlank { null }
                            ?: listOf(settings.vehicleBrand, settings.vehicleModel)
                                .filter { it.isNotBlank() }
                                .joinToString(" ")
                                .ifBlank { stringResource(R.string.search_mode_my_car) },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.parked_car_no_garage_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        settings.vehicles.forEachIndexed { index, v ->
                            val label = v.displayLabel()
                                .ifBlank { stringResource(R.string.vehicle_unnamed, index + 1) }
                            FilterChip(
                                selected = v.id == selectedVehicleId,
                                onClick = {
                                    selectedVehicleId = v.id
                                    statusMessage = null
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("parked_car_vehicle_${v.id}"),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (pendingCandidate != null && parked == null) {
                    Text(
                        text = stringResource(R.string.parked_car_candidate_detected),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                val ok = parkActions.save(
                                    vehicleId = selectedVehicleId,
                                    latitude = pendingCandidate.latitude,
                                    longitude = pendingCandidate.longitude,
                                )
                                intentCandidateDismissed = true
                                statusMessage = context.getString(
                                    if (ok) R.string.parked_car_saved
                                    else R.string.parked_car_location_unavailable
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("parked_car_candidate_save_btn"),
                        ) {
                            Text(stringResource(R.string.action_save))
                        }
                        OutlinedButton(
                            onClick = {
                                parkActions.ignore()
                                intentCandidateDismissed = true
                                statusMessage = context.getString(R.string.parked_car_candidate_ignored)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("parked_car_candidate_ignore_btn"),
                        ) {
                            Text(stringResource(R.string.action_ignore))
                        }
                    }
                }

                if (parked != null) {
                    val p = parked
                    Text(
                        text = stringResource(
                            R.string.parked_car_parked_at,
                            p.formattedTimestamp(DateFormat.MEDIUM, DateFormat.SHORT),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(
                            R.string.parked_car_coords,
                            String.format("%.5f", p.latitude),
                            String.format("%.5f", p.longitude),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val undergroundInfoParts = buildList {
                        if (p.isUnderground) add(stringResource(R.string.parked_car_underground_toggle))
                        if (!p.level.isNullOrBlank()) add("${stringResource(R.string.parked_car_level_label)}: ${p.level}")
                        if (!p.spotNumber.isNullOrBlank()) add("${stringResource(R.string.parked_car_spot_number_label)}: ${p.spotNumber}")
                        if (!p.spotColor.isNullOrBlank()) add("${stringResource(R.string.parked_car_spot_color_label)}: ${p.spotColor}")
                    }
                    if (undergroundInfoParts.isNotEmpty()) {
                        Text(
                            text = undergroundInfoParts.joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else if (pendingCandidate == null) {
                    Text(
                        text = stringResource(R.string.parked_car_none_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (mapLat != null && mapLon != null) {
                    Text(
                        text = stringResource(R.string.parked_car_map_section),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    ParkedCarMapPreview(
                        carLatitude = mapLat,
                        carLongitude = mapLon,
                        userLatitude = userLocation?.latitude,
                        userLongitude = userLocation?.longitude,
                        onMapInteractionChanged = { interacting ->
                            columnScrollEnabled = !interacting
                        },
                    )
                }

                // Underground parking controls & notes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_poi_parking),
                            contentDescription = null,
                            tint = if (isUnderground) ParkedAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = stringResource(R.string.parked_car_underground_toggle),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Switch(
                        checked = isUnderground,
                        onCheckedChange = { isUnderground = it },
                        modifier = Modifier.testTag("parked_car_underground_switch"),
                    )
                }

                if (isUnderground || level.isNotBlank() || spotNumber.isNotBlank() || spotColor.isNotBlank() || !photoUri.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.parked_car_underground_details_section),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )

                    OutlinedTextField(
                        value = level,
                        onValueChange = { level = it },
                        label = { Text(stringResource(R.string.parked_car_level_label)) },
                        placeholder = { Text(stringResource(R.string.parked_car_level_hint)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("parked_car_level_input"),
                        singleLine = true,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = spotNumber,
                            onValueChange = { spotNumber = it },
                            label = { Text(stringResource(R.string.parked_car_spot_number_label)) },
                            placeholder = { Text(stringResource(R.string.parked_car_spot_number_hint)) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("parked_car_spot_number_input"),
                            singleLine = true,
                        )

                        OutlinedTextField(
                            value = spotColor,
                            onValueChange = { spotColor = it },
                            label = { Text(stringResource(R.string.parked_car_spot_color_label)) },
                            placeholder = { Text(stringResource(R.string.parked_car_spot_color_hint)) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("parked_car_spot_color_input"),
                            singleLine = true,
                        )
                    }

                    Text(
                        text = stringResource(R.string.parked_car_photo_label),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )

                    if (!photoUri.isNullOrBlank()) {
                        AsyncImage(
                            model = photoUri,
                            contentDescription = stringResource(R.string.parked_car_photo_label),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        OutlinedButton(
                            onClick = { photoUri = null },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("parked_car_remove_photo_btn"),
                        ) {
                            Text(stringResource(R.string.parked_car_remove_photo))
                        }
                    } else {
                        OutlinedButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("parked_car_pick_photo_btn"),
                        ) {
                            Text(stringResource(R.string.parked_car_pick_photo))
                        }
                    }
                }

                statusMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Button(
                    onClick = {
                        if (isSaving) return@Button
                        isSaving = true
                        statusMessage = null
                        scope.launch {
                            try {
                                val candidate = candidateStore.get()
                                val loc = LocationHelper.getCurrentLocation(context)
                                val coords = ParkCandidateSaveHelper.resolveSaveCoords(
                                    candidate = when {
                                        candidateLatitude != null && candidateLongitude != null ->
                                            ParkCandidate(
                                                vehicleId = selectedVehicleId,
                                                latitude = candidateLatitude,
                                                longitude = candidateLongitude,
                                                createdAtEpochMs = System.currentTimeMillis(),
                                            )
                                        else -> candidate
                                    },
                                    vehicleId = selectedVehicleId,
                                    fallbackLat = loc?.latitude ?: settings.lastKnownLat,
                                    fallbackLon = loc?.longitude ?: settings.lastKnownLon,
                                )
                                if (coords == null) {
                                    statusMessage =
                                        context.getString(R.string.parked_car_location_unavailable)
                                } else {
                                    val (lat, lon) = coords
                                    settingsManager.saveParkedPosition(
                                        latitude = lat,
                                        longitude = lon,
                                        vehicleId = selectedVehicleId,
                                        isUnderground = isUnderground,
                                        level = level,
                                        spotNumber = spotNumber,
                                        spotColor = spotColor,
                                        photoUri = photoUri,
                                    )
                                    if (candidate != null && candidate.vehicleId == selectedVehicleId) {
                                        candidateStore.clear()
                                    } else if (loc != null && candidateLatitude == null) {
                                        settingsManager.saveLastKnownLocation(lat, lon)
                                    }
                                    statusMessage = context.getString(R.string.parked_car_saved)
                                }
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("parked_car_remember_btn"),
                    enabled = !isSaving,
                ) {
                    Text(
                        if (isSaving) {
                            stringResource(R.string.parked_car_saving)
                        } else if (parked != null) {
                            stringResource(R.string.parked_car_update)
                        } else {
                            stringResource(R.string.parked_car_remember)
                        }
                    )
                }

                if (parked != null) {
                    OutlinedButton(
                        onClick = {
                            val uri = IntentNavigationHelper.getNavigationUri(
                                parked.latitude,
                                parked.longitude,
                            )
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("parked_car_navigate_btn"),
                    ) {
                        Text(stringResource(R.string.parked_car_navigate))
                    }
                    OutlinedButton(
                        onClick = {
                            settingsManager.clearParkedPosition(selectedVehicleId)
                            statusMessage = context.getString(R.string.parked_car_cleared)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("parked_car_clear_btn"),
                    ) {
                        Text(stringResource(R.string.parked_car_clear))
                    }
                }
            }
        }
    }
}
