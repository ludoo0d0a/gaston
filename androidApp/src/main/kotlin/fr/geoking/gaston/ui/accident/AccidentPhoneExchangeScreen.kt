package fr.geoking.gaston.ui.accident

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.geoking.gaston.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccidentPhoneExchangeScreen(
    viewModel: AccidentViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val profile by viewModel.profile.collectAsState()
    val otherPhone by viewModel.otherDriverPhone.collectAsState()

    val shareBody = if (profile.phone.isNotBlank()) {
        stringResource(R.string.accident_share_phone_body, profile.fullName.ifBlank { "—" }, profile.phone)
    } else {
        stringResource(R.string.accident_share_phone_body_empty)
    }
    val shareSubject = stringResource(R.string.accident_share_phone_subject)
    val smsFollowupBody = stringResource(R.string.accident_sms_followup_body)
    val otherDriverLabel = stringResource(R.string.accident_other_driver_phone)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.accident_phone_exchange_title)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .testTag("accident_phone_exchange"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.accident_phone_exchange_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.accident_your_phone),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = profile.phone.ifBlank { stringResource(R.string.accident_phone_missing) },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (profile.phone.isBlank()) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Button(
                        onClick = {
                            shareText(
                                context,
                                shareSubject,
                                shareBody,
                            )
                        },
                        enabled = profile.phone.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("accident_share_phone_btn"),
                    ) {
                        Text(stringResource(R.string.accident_share_my_phone))
                    }
                    OutlinedButton(
                        onClick = {
                            sendSms(context, otherPhone, shareBody)
                        },
                        enabled = profile.phone.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        Text(stringResource(R.string.accident_sms_my_phone))
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.accident_other_driver_phone),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedTextField(
                        value = otherPhone,
                        onValueChange = viewModel::setOtherDriverPhone,
                        label = { Text(stringResource(R.string.accident_field_phone)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("accident_other_phone_field"),
                    )
                    RowButtons(
                        otherPhone = otherPhone,
                        onDial = { dial(context, otherPhone) },
                        onSms = {
                            sendSms(
                                context,
                                otherPhone,
                                smsFollowupBody,
                            )
                        },
                        onCopy = {
                            copyText(
                                context,
                                otherDriverLabel,
                                otherPhone,
                            )
                        },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RowButtons(
    otherPhone: String,
    onDial: () -> Unit,
    onSms: () -> Unit,
    onCopy: () -> Unit,
) {
    val enabled = otherPhone.isNotBlank()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onDial,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(stringResource(R.string.accident_call_other))
        }
        OutlinedButton(
            onClick = onSms,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(stringResource(R.string.accident_sms_other))
        }
        OutlinedButton(
            onClick = onCopy,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(stringResource(R.string.accident_copy))
        }
    }
}
