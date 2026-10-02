package fr.geoking.gaston.ui.accident

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.geoking.gaston.R
import fr.geoking.gaston.feature.accident.AccidentProfile

private data class PaperStep(
    val boxLabel: String,
    val guidance: String,
    val prefillLines: List<Pair<String, String>>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccidentPaperAideScreen(
    profile: AccidentProfile,
    vehicle: fr.geoking.gaston.UserVehicle?,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val context = LocalContext.current
    val steps = buildPaperSteps(profile, vehicle)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.accident_paper_aide_title)) },
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
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .testTag("accident_paper_aide"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.accident_paper_aide_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            if (!profile.isPartiallyFilled() && vehicle?.hasIdentity() != true && vehicle?.hasInsurance() != true) {
                item {
                    OutlinedButton(
                        onClick = onOpenProfile,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        Text(stringResource(R.string.accident_prefill_title))
                    }
                }
            }

            items(steps) { step ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = step.boxLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = step.guidance,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        step.prefillLines.forEach { (label, value) ->
                            PrefillValueRow(
                                label = label,
                                value = value,
                                onCopy = {
                                    copyText(context, label, value)
                                },
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.accident_paper_aide_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun buildPaperSteps(
    profile: AccidentProfile,
    vehicle: fr.geoking.gaston.UserVehicle?,
): List<PaperStep> {
    val name = stringResource(R.string.accident_field_full_name)
    val address = stringResource(R.string.accident_field_address)
    val phone = stringResource(R.string.accident_field_phone)
    val license = stringResource(R.string.accident_field_license)
    val licenseCountry = stringResource(R.string.accident_field_license_country)
    val plate = stringResource(R.string.accident_field_plate)
    val make = stringResource(R.string.accident_field_make)
    val model = stringResource(R.string.accident_field_model)
    val color = stringResource(R.string.accident_field_color)
    val serial = stringResource(R.string.vehicle_serial_number)
    val fuelCardLabel = stringResource(R.string.vehicle_fuel_card)
    val energyLabel = stringResource(R.string.vehicle_energy_type)
    val insurer = stringResource(R.string.accident_field_insurer)
    val policy = stringResource(R.string.accident_field_policy)
    val greenCard = stringResource(R.string.accident_field_green_card)
    val insurerPhone = stringResource(R.string.accident_field_insurer_phone)
    val agency = stringResource(R.string.accident_field_agency)
    val fuelCardValue = when (vehicle?.fuelCard) {
        fr.geoking.gaston.FuelCard.Routex -> stringResource(R.string.fuel_card_routex)
        else -> ""
    }
    val energyValue = when (vehicle?.energy) {
        "electric" -> stringResource(R.string.vehicle_energy_electric)
        "hybrid" -> stringResource(R.string.energy_hybrid)
        "gas" -> stringResource(R.string.energy_gas)
        else -> vehicle?.energy.orEmpty()
    }

    return listOf(
        PaperStep(
            boxLabel = stringResource(R.string.accident_paper_box_datetime),
            guidance = stringResource(R.string.accident_paper_box_datetime_help),
            prefillLines = emptyList(),
        ),
        PaperStep(
            boxLabel = stringResource(R.string.accident_paper_box_place),
            guidance = stringResource(R.string.accident_paper_box_place_help),
            prefillLines = emptyList(),
        ),
        PaperStep(
            boxLabel = stringResource(R.string.accident_paper_box_injuries),
            guidance = stringResource(R.string.accident_paper_box_injuries_help),
            prefillLines = emptyList(),
        ),
        PaperStep(
            boxLabel = stringResource(R.string.accident_paper_box_other_damage),
            guidance = stringResource(R.string.accident_paper_box_other_damage_help),
            prefillLines = emptyList(),
        ),
        PaperStep(
            boxLabel = stringResource(R.string.accident_paper_box_witnesses),
            guidance = stringResource(R.string.accident_paper_box_witnesses_help),
            prefillLines = emptyList(),
        ),
        PaperStep(
            boxLabel = stringResource(R.string.accident_paper_box_insured),
            guidance = stringResource(R.string.accident_paper_box_insured_help),
            prefillLines = listOf(
                name to profile.fullName,
                address to profile.address,
                phone to profile.phone,
            ).filter { it.second.isNotBlank() },
        ),
        PaperStep(
            boxLabel = stringResource(R.string.accident_paper_box_vehicle),
            guidance = stringResource(R.string.accident_paper_box_vehicle_help),
            prefillLines = listOf(
                plate to (vehicle?.plate.orEmpty()),
                make to (vehicle?.brand.orEmpty()),
                model to (vehicle?.model.orEmpty()),
                color to (vehicle?.color.orEmpty()),
                serial to (vehicle?.serialNumber.orEmpty()),
                fuelCardLabel to fuelCardValue,
                energyLabel to energyValue,
            ).filter { it.second.isNotBlank() },
        ),
        PaperStep(
            boxLabel = stringResource(R.string.accident_paper_box_insurance),
            guidance = stringResource(R.string.accident_paper_box_insurance_help),
            prefillLines = listOf(
                insurer to (vehicle?.insurerName.orEmpty()),
                policy to (vehicle?.policyNumber.orEmpty()),
                greenCard to (vehicle?.greenCardNumber.orEmpty()),
                insurerPhone to (vehicle?.insurerPhone.orEmpty()),
                agency to (vehicle?.agencyName.orEmpty()),
            ).filter { it.second.isNotBlank() },
        ),
        PaperStep(
            boxLabel = stringResource(R.string.accident_paper_box_driver),
            guidance = stringResource(R.string.accident_paper_box_driver_help),
            prefillLines = listOf(
                name to profile.fullName,
                license to profile.licenseNumber,
                licenseCountry to profile.licenseCountry,
                phone to profile.phone,
            ).filter { it.second.isNotBlank() },
        ),
        PaperStep(
            boxLabel = stringResource(R.string.accident_paper_box_circumstances),
            guidance = stringResource(R.string.accident_paper_box_circumstances_help),
            prefillLines = emptyList(),
        ),
        PaperStep(
            boxLabel = stringResource(R.string.accident_paper_box_sketch),
            guidance = stringResource(R.string.accident_paper_box_sketch_help),
            prefillLines = emptyList(),
        ),
        PaperStep(
            boxLabel = stringResource(R.string.accident_paper_box_observations),
            guidance = stringResource(R.string.accident_paper_box_observations_help),
            prefillLines = emptyList(),
        ),
        PaperStep(
            boxLabel = stringResource(R.string.accident_paper_box_signatures),
            guidance = stringResource(R.string.accident_paper_box_signatures_help),
            prefillLines = emptyList(),
        ),
    )
}
