package fr.geoking.gaston.ui.accident

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.geoking.gaston.FuelCard
import fr.geoking.gaston.R
import fr.geoking.gaston.feature.accident.AccidentProfile

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AccidentProfileScreen(
    viewModel: AccidentViewModel,
    onBack: () -> Unit,
) {
    val saved by viewModel.profile.collectAsState()
    val vehicles by viewModel.vehicles.collectAsState()
    val selectedVehicle by viewModel.selectedVehicle.collectAsState()
    val savedMessage by viewModel.savedMessage.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val savedLabel = stringResource(R.string.accident_profile_saved)

    var fullName by remember(saved) { mutableStateOf(saved.fullName) }
    var address by remember(saved) { mutableStateOf(saved.address) }
    var phone by remember(saved) { mutableStateOf(saved.phone) }
    var email by remember(saved) { mutableStateOf(saved.email) }
    var licenseNumber by remember(saved) { mutableStateOf(saved.licenseNumber) }
    var licenseCountry by remember(saved) { mutableStateOf(saved.licenseCountry) }
    var emergencyContactName by remember(saved) { mutableStateOf(saved.emergencyContactName) }
    var emergencyContactPhone by remember(saved) { mutableStateOf(saved.emergencyContactPhone) }

    val vehicleId = selectedVehicle?.id.orEmpty()
    var vehiclePlate by remember(vehicleId, selectedVehicle?.plate) {
        mutableStateOf(selectedVehicle?.plate.orEmpty())
    }
    var vehicleMake by remember(vehicleId, selectedVehicle?.brand) {
        mutableStateOf(selectedVehicle?.brand.orEmpty())
    }
    var vehicleModel by remember(vehicleId, selectedVehicle?.model) {
        mutableStateOf(selectedVehicle?.model.orEmpty())
    }
    var vehicleColor by remember(vehicleId, selectedVehicle?.color) {
        mutableStateOf(selectedVehicle?.color.orEmpty())
    }
    var vehicleSerial by remember(vehicleId, selectedVehicle?.serialNumber) {
        mutableStateOf(selectedVehicle?.serialNumber.orEmpty())
    }
    var fuelCard by remember(vehicleId, selectedVehicle?.fuelCard) {
        mutableStateOf(selectedVehicle?.fuelCard ?: FuelCard.None)
    }
    var vehicleEnergy by remember(vehicleId, selectedVehicle?.energy) {
        mutableStateOf(selectedVehicle?.energy ?: "gas")
    }
    var insurerName by remember(vehicleId, selectedVehicle?.insurerName) {
        mutableStateOf(selectedVehicle?.insurerName.orEmpty())
    }
    var policyNumber by remember(vehicleId, selectedVehicle?.policyNumber) {
        mutableStateOf(selectedVehicle?.policyNumber.orEmpty())
    }
    var greenCardNumber by remember(vehicleId, selectedVehicle?.greenCardNumber) {
        mutableStateOf(selectedVehicle?.greenCardNumber.orEmpty())
    }
    var insurerPhone by remember(vehicleId, selectedVehicle?.insurerPhone) {
        mutableStateOf(selectedVehicle?.insurerPhone.orEmpty())
    }
    var agencyName by remember(vehicleId, selectedVehicle?.agencyName) {
        mutableStateOf(selectedVehicle?.agencyName.orEmpty())
    }
    var agencyPhone by remember(vehicleId, selectedVehicle?.agencyPhone) {
        mutableStateOf(selectedVehicle?.agencyPhone.orEmpty())
    }

    LaunchedEffect(savedMessage) {
        if (savedMessage) {
            snackbar.showSnackbar(savedLabel)
            viewModel.clearSavedMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.accident_prefill_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .testTag("accident_profile_form"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.accident_prefill_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle(stringResource(R.string.accident_section_personal))
            ProfileField(stringResource(R.string.accident_field_full_name), fullName) { fullName = it }
            ProfileField(stringResource(R.string.accident_field_address), address) { address = it }
            ProfileField(
                stringResource(R.string.accident_field_phone),
                phone,
                KeyboardType.Phone,
            ) { phone = it }
            ProfileField(
                stringResource(R.string.accident_field_email),
                email,
                KeyboardType.Email,
            ) { email = it }
            ProfileField(stringResource(R.string.accident_field_license), licenseNumber) { licenseNumber = it }
            ProfileField(stringResource(R.string.accident_field_license_country), licenseCountry) { licenseCountry = it }

            SectionTitle(stringResource(R.string.accident_section_vehicle))
            Text(
                text = stringResource(R.string.accident_vehicle_from_garage),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (vehicles.isEmpty()) {
                Text(
                    text = stringResource(R.string.accident_vehicle_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    vehicles.forEachIndexed { index, vehicle ->
                        val label = vehicle.displayLabel()
                            .ifBlank { stringResource(R.string.vehicle_unnamed, index + 1) }
                        FilterChip(
                            selected = vehicle.id == (selectedVehicle?.id ?: saved.selectedVehicleId),
                            onClick = { viewModel.selectVehicle(vehicle.id) },
                            label = { Text(label) },
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = { viewModel.addVehicle() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.vehicle_add))
            }

            ProfileField(stringResource(R.string.accident_field_plate), vehiclePlate) { vehiclePlate = it }
            ProfileField(stringResource(R.string.accident_field_make), vehicleMake) { vehicleMake = it }
            ProfileField(stringResource(R.string.accident_field_model), vehicleModel) { vehicleModel = it }
            ProfileField(stringResource(R.string.accident_field_color), vehicleColor) { vehicleColor = it }
            ProfileField(stringResource(R.string.vehicle_serial_number), vehicleSerial) { vehicleSerial = it }

            Text(
                text = stringResource(R.string.vehicle_fuel_card),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FuelCard.entries.forEach { card ->
                    val label = when (card) {
                        FuelCard.None -> stringResource(R.string.fuel_card_none)
                        FuelCard.Routex -> stringResource(R.string.fuel_card_routex)
                    }
                    FilterChip(
                        selected = fuelCard == card,
                        onClick = { fuelCard = card },
                        label = { Text(label) },
                    )
                }
            }

            Text(
                text = stringResource(R.string.vehicle_energy_type),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "gas" to R.string.energy_gas,
                    "electric" to R.string.vehicle_energy_electric,
                    "hybrid" to R.string.energy_hybrid,
                ).forEach { (id, labelRes) ->
                    FilterChip(
                        selected = vehicleEnergy == id,
                        onClick = { vehicleEnergy = id },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }

            SectionTitle(stringResource(R.string.accident_section_insurance))
            ProfileField(stringResource(R.string.accident_field_insurer), insurerName) { insurerName = it }
            ProfileField(stringResource(R.string.accident_field_policy), policyNumber) { policyNumber = it }
            ProfileField(stringResource(R.string.accident_field_green_card), greenCardNumber) { greenCardNumber = it }
            ProfileField(
                stringResource(R.string.accident_field_insurer_phone),
                insurerPhone,
                KeyboardType.Phone,
            ) { insurerPhone = it }
            ProfileField(stringResource(R.string.accident_field_agency), agencyName) { agencyName = it }
            ProfileField(
                stringResource(R.string.accident_field_agency_phone),
                agencyPhone,
                KeyboardType.Phone,
            ) { agencyPhone = it }

            SectionTitle(stringResource(R.string.accident_section_emergency_contact))
            ProfileField(stringResource(R.string.accident_field_emergency_name), emergencyContactName) {
                emergencyContactName = it
            }
            ProfileField(
                stringResource(R.string.accident_field_emergency_phone),
                emergencyContactPhone,
                KeyboardType.Phone,
            ) { emergencyContactPhone = it }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val vehicleId = viewModel.updateSelectedVehicle { current ->
                        current.copy(
                            brand = vehicleMake.trim(),
                            model = vehicleModel.trim(),
                            plate = vehiclePlate.trim(),
                            color = vehicleColor.trim(),
                            serialNumber = vehicleSerial.trim(),
                            fuelCard = fuelCard,
                            energy = vehicleEnergy,
                            insurerName = insurerName.trim(),
                            policyNumber = policyNumber.trim(),
                            greenCardNumber = greenCardNumber.trim(),
                            insurerPhone = insurerPhone.trim(),
                            agencyName = agencyName.trim(),
                            agencyPhone = agencyPhone.trim(),
                        )
                    }
                    viewModel.saveProfile(
                        AccidentProfile(
                            fullName = fullName.trim(),
                            address = address.trim(),
                            phone = phone.trim(),
                            email = email.trim(),
                            licenseNumber = licenseNumber.trim(),
                            licenseCountry = licenseCountry.trim(),
                            selectedVehicleId = vehicleId,
                            emergencyContactName = emergencyContactName.trim(),
                            emergencyContactPhone = emergencyContactPhone.trim(),
                        ),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("accident_profile_save_btn"),
            ) {
                Text(stringResource(R.string.action_save))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ProfileField(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}
