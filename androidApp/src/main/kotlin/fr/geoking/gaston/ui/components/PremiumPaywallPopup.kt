package fr.geoking.gaston.ui.components

import fr.geoking.gaston.R
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import fr.geoking.gaston.premium.BillingManager
import kotlinx.coroutines.launch

@Composable
fun PremiumPaywallPopup(
    billingManager: BillingManager,
    onDismiss: () -> Unit,
    onPurchaseSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isPurchasing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Gaston Premium",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                PremiumFeatureItem("🚫 No Ads", "Clean and focused experience")
                PremiumFeatureItem("⭐ Favorites", "Save your favorite stations")
                PremiumFeatureItem("📈 Price Estimation", "Real-time fuel price outlook")

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        isPurchasing = true
                        error = null
                        scope.launch {
                            billingManager.purchasePremium()
                                .onSuccess { onPurchaseSuccess() }
                                .onFailure {
                                    error = it.message ?: "Purchase failed"
                                    isPurchasing = false
                                }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isPurchasing
                ) {
                    if (isPurchasing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.action_upgrade_premium))
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isPurchasing
                ) {
                    Text(stringResource(R.string.action_maybe_later))
                }
            }
        }
    }
}

@Composable
private fun PremiumFeatureItem(title: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(text = description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
