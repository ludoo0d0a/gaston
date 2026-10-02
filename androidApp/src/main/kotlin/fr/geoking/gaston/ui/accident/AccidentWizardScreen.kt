package fr.geoking.gaston.ui.accident

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.geoking.gaston.R
import fr.geoking.gaston.feature.accident.AccidentProfile

private const val WIZARD_STEP_COUNT = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccidentWizardScreen(
    profile: AccidentProfile,
    vehicle: fr.geoking.gaston.UserVehicle?,
    onBack: () -> Unit,
    onOpenEmergency: () -> Unit,
    onOpenPhoneExchange: () -> Unit,
    onOpenPaperAide: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.accident_wizard_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (step == 0) onBack() else step--
                    }) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .testTag("accident_wizard"),
        ) {
            Text(
                text = stringResource(R.string.accident_wizard_step_of, step + 1, WIZARD_STEP_COUNT),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (step + 1).toFloat() / WIZARD_STEP_COUNT },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (step) {
                    0 -> WizardSafetyStep(onOpenEmergency = onOpenEmergency)
                    1 -> WizardDocumentStep()
                    2 -> WizardPhoneStep(profile = profile, onOpenPhoneExchange = onOpenPhoneExchange)
                    3 -> WizardConstatStep(
                        profile = profile,
                        vehicle = vehicle,
                        onOpenPaperAide = onOpenPaperAide,
                        onOpenProfile = onOpenProfile,
                    )
                    else -> WizardAfterStep(vehicle = vehicle)
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (step > 0) {
                    OutlinedButton(
                        onClick = { step-- },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                    ) {
                        Text(stringResource(R.string.accident_wizard_previous))
                    }
                }
                Button(
                    onClick = {
                        if (step < WIZARD_STEP_COUNT - 1) step++ else onBack()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("accident_wizard_next_btn"),
                ) {
                    Text(
                        if (step < WIZARD_STEP_COUNT - 1) {
                            stringResource(R.string.accident_wizard_next)
                        } else {
                            stringResource(R.string.accident_wizard_done)
                        },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun WizardSafetyStep(onOpenEmergency: () -> Unit) {
    WizardTitle(stringResource(R.string.accident_wizard_safety_title))
    WizardBullet(stringResource(R.string.accident_wizard_safety_1))
    WizardBullet(stringResource(R.string.accident_wizard_safety_2))
    WizardBullet(stringResource(R.string.accident_wizard_safety_3))
    WizardBullet(stringResource(R.string.accident_wizard_safety_4))
    Button(
        onClick = onOpenEmergency,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(stringResource(R.string.accident_open_emergency))
    }
}

@Composable
private fun WizardDocumentStep() {
    WizardTitle(stringResource(R.string.accident_wizard_photos_title))
    WizardBullet(stringResource(R.string.accident_wizard_photos_1))
    WizardBullet(stringResource(R.string.accident_wizard_photos_2))
    WizardBullet(stringResource(R.string.accident_wizard_photos_3))
    WizardBullet(stringResource(R.string.accident_wizard_photos_4))
    WizardBullet(stringResource(R.string.accident_wizard_photos_5))
}

@Composable
private fun WizardPhoneStep(profile: AccidentProfile, onOpenPhoneExchange: () -> Unit) {
    WizardTitle(stringResource(R.string.accident_wizard_phone_title))
    WizardBullet(stringResource(R.string.accident_wizard_phone_1))
    WizardBullet(stringResource(R.string.accident_wizard_phone_2))
    if (profile.phone.isNotBlank()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.accident_your_phone),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = profile.phone,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
    Button(
        onClick = onOpenPhoneExchange,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(stringResource(R.string.accident_phone_exchange_title))
    }
}

@Composable
private fun WizardConstatStep(
    profile: AccidentProfile,
    vehicle: fr.geoking.gaston.UserVehicle?,
    onOpenPaperAide: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    WizardTitle(stringResource(R.string.accident_wizard_constat_title))
    WizardBullet(stringResource(R.string.accident_wizard_constat_1))
    WizardBullet(stringResource(R.string.accident_wizard_constat_2))
    WizardBullet(stringResource(R.string.accident_wizard_constat_3))
    val prefillReady = profile.isPartiallyFilled() ||
        vehicle?.hasIdentity() == true ||
        vehicle?.hasInsurance() == true
    if (!prefillReady) {
        OutlinedButton(
            onClick = onOpenProfile,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(stringResource(R.string.accident_prefill_title))
        }
    }
    Button(
        onClick = onOpenPaperAide,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(stringResource(R.string.accident_paper_aide_title))
    }
}

@Composable
private fun WizardAfterStep(vehicle: fr.geoking.gaston.UserVehicle?) {
    WizardTitle(stringResource(R.string.accident_wizard_after_title))
    WizardBullet(stringResource(R.string.accident_wizard_after_1))
    WizardBullet(stringResource(R.string.accident_wizard_after_2))
    WizardBullet(stringResource(R.string.accident_wizard_after_3))
    val insurerPhone = vehicle?.insurerPhone.orEmpty()
    val agencyPhone = vehicle?.agencyPhone.orEmpty()
    if (insurerPhone.isNotBlank() || agencyPhone.isNotBlank()) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val number = insurerPhone.ifBlank { agencyPhone }
        Button(
            onClick = { dial(context, number) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(stringResource(R.string.accident_call_insurer))
        }
    }
}

@Composable
private fun WizardTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun WizardBullet(text: String) {
    Text(
        text = "• $text",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}
