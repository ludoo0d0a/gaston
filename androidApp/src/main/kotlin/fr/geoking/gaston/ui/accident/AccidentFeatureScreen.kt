package fr.geoking.gaston.ui.accident

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.geoking.gaston.R
import fr.geoking.gaston.feature.accident.AccidentProfile
import fr.geoking.gaston.ui.dashboard.GastonTheme
import org.koin.androidx.compose.koinViewModel

private val AccidentAccent = Color(0xFFE65100)
private val AccidentOnAccent = Color.White

private enum class AccidentDest {
    Hub,
    Profile,
    Wizard,
    PhoneExchange,
    PaperAide,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccidentFeatureScreen(
    onBack: () -> Unit,
    onOpenEmergency: () -> Unit,
    viewModel: AccidentViewModel = koinViewModel(),
) {
    var dest by remember { mutableStateOf(AccidentDest.Hub) }
    val profile by viewModel.profile.collectAsState()
    val selectedVehicle by viewModel.selectedVehicle.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.seedFromVehicleSettingsIfNeeded()
    }

    BackHandler {
        if (dest == AccidentDest.Hub) onBack() else dest = AccidentDest.Hub
    }

    GastonTheme {
        when (dest) {
            AccidentDest.Hub -> AccidentHubScreen(
                profile = profile,
                vehicle = selectedVehicle,
                onBack = onBack,
                onOpenProfile = { dest = AccidentDest.Profile },
                onOpenWizard = { dest = AccidentDest.Wizard },
                onOpenPhoneExchange = { dest = AccidentDest.PhoneExchange },
                onOpenPaperAide = { dest = AccidentDest.PaperAide },
                onOpenEmergency = onOpenEmergency,
            )
            AccidentDest.Profile -> AccidentProfileScreen(
                viewModel = viewModel,
                onBack = { dest = AccidentDest.Hub },
            )
            AccidentDest.Wizard -> AccidentWizardScreen(
                profile = profile,
                vehicle = selectedVehicle,
                onBack = { dest = AccidentDest.Hub },
                onOpenEmergency = onOpenEmergency,
                onOpenPhoneExchange = { dest = AccidentDest.PhoneExchange },
                onOpenPaperAide = { dest = AccidentDest.PaperAide },
                onOpenProfile = { dest = AccidentDest.Profile },
            )
            AccidentDest.PhoneExchange -> AccidentPhoneExchangeScreen(
                viewModel = viewModel,
                onBack = { dest = AccidentDest.Hub },
            )
            AccidentDest.PaperAide -> AccidentPaperAideScreen(
                profile = profile,
                vehicle = selectedVehicle,
                onBack = { dest = AccidentDest.Hub },
                onOpenProfile = { dest = AccidentDest.Profile },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccidentHubScreen(
    profile: AccidentProfile,
    vehicle: fr.geoking.gaston.UserVehicle?,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenWizard: () -> Unit,
    onOpenPhoneExchange: () -> Unit,
    onOpenPaperAide: () -> Unit,
    onOpenEmergency: () -> Unit,
) {
    val prefillReady = profile.isPartiallyFilled() ||
        vehicle?.hasIdentity() == true ||
        vehicle?.hasInsurance() == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.accident_title)) },
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
                .testTag("accident_hub"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            item {
                Text(
                    text = stringResource(R.string.accident_hub_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            item {
                Card(
                    onClick = onOpenWizard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("accident_wizard_cta"),
                    colors = CardDefaults.cardColors(containerColor = AccidentAccent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_warning),
                            contentDescription = null,
                            tint = AccidentOnAccent,
                            modifier = Modifier.size(36.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.accident_had_accident),
                                style = MaterialTheme.typography.titleMedium,
                                color = AccidentOnAccent,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(R.string.accident_had_accident_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = AccidentOnAccent.copy(alpha = 0.92f),
                            )
                        }
                    }
                }
            }

            item {
                HubActionCard(
                    title = stringResource(R.string.accident_prefill_title),
                    subtitle = if (prefillReady) {
                        stringResource(R.string.accident_prefill_status_ready)
                    } else {
                        stringResource(R.string.accident_prefill_status_empty)
                    },
                    iconRes = R.drawable.ic_directions_car,
                    onClick = onOpenProfile,
                    testTag = "accident_prefill_btn",
                )
            }

            item {
                HubActionCard(
                    title = stringResource(R.string.accident_phone_exchange_title),
                    subtitle = stringResource(R.string.accident_phone_exchange_subtitle),
                    iconRes = R.drawable.ic_phone,
                    onClick = onOpenPhoneExchange,
                    testTag = "accident_phone_btn",
                )
            }

            item {
                HubActionCard(
                    title = stringResource(R.string.accident_paper_aide_title),
                    subtitle = stringResource(R.string.accident_paper_aide_subtitle),
                    iconRes = R.drawable.ic_category,
                    onClick = onOpenPaperAide,
                    testTag = "accident_paper_btn",
                )
            }

            item {
                OutlinedButton(
                    onClick = onOpenEmergency,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("accident_open_emergency_btn"),
                ) {
                    Text(stringResource(R.string.accident_open_emergency))
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HubActionCard(
    title: String,
    subtitle: String,
    iconRes: Int,
    onClick: () -> Unit,
    testTag: String,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal fun copyText(context: Context, label: String, text: String) {
    if (text.isBlank()) return
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

internal fun shareText(context: Context, subject: String, text: String) {
    if (text.isBlank()) return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(
        Intent.createChooser(intent, subject).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
}

internal fun dial(context: Context, number: String) {
    val sanitized = number.replace(" ", "")
    if (sanitized.isBlank()) return
    try {
        context.startActivity(
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$sanitized")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    } catch (_: Exception) {
        // Tablet / no dialer
    }
}

internal fun sendSms(context: Context, number: String, body: String) {
    val sanitized = number.replace(" ", "")
    val uri = if (sanitized.isBlank()) {
        Uri.parse("smsto:")
    } else {
        Uri.parse("smsto:$sanitized")
    }
    try {
        context.startActivity(
            Intent(Intent.ACTION_SENDTO, uri).apply {
                putExtra("sms_body", body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    } catch (_: Exception) {
        shareText(context, context.getString(R.string.accident_share_phone_subject), body)
    }
}

@Composable
internal fun PrefillValueRow(
    label: String,
    value: String,
    onCopy: (() -> Unit)? = null,
) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (onCopy != null) {
            OutlinedButton(onClick = onCopy) {
                Text(stringResource(R.string.accident_copy))
            }
        }
    }
}
