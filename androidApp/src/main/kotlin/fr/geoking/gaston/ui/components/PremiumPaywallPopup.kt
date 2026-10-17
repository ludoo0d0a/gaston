package fr.geoking.gaston.ui.components

import fr.geoking.gaston.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import fr.geoking.gaston.entitlements.Feature
import fr.geoking.gaston.premium.BillingManager
import fr.geoking.gaston.premium.PremiumPurchaseResult
import kotlinx.coroutines.launch

@Composable
fun PremiumPaywallPopup(
    billingManager: BillingManager,
    trigger: Feature? = null,
    onDismiss: () -> Unit,
    onPurchaseSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isPurchasing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val paymentPendingMessage = stringResource(R.string.premium_payment_pending)

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
                    text = stringResource(R.string.premium_brand_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (trigger != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = getTriggerReasonText(trigger),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                PremiumFeatureItem("🚫 No Ads", "Clean and focused experience")
                PremiumFeatureItem("⭐ Unlimited Favorites", "Save and organize all your favorite stations")
                PremiumFeatureItem("📈 Price Outlook & Alerts", "Real-time fuel price forecasts and price drop alerts")
                PremiumFeatureItem("🚘 Android Auto Full Map", "Interactive map, all POIs, and custom route planning")

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
                            when (val result = billingManager.purchasePremium()) {
                                PremiumPurchaseResult.Success -> onPurchaseSuccess()
                                PremiumPurchaseResult.Cancelled -> {
                                    isPurchasing = false
                                }
                                PremiumPurchaseResult.Pending -> {
                                    error = paymentPendingMessage
                                    isPurchasing = false
                                }
                                is PremiumPurchaseResult.Error -> {
                                    error = result.message
                                    isPurchasing = false
                                }
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
                    onClick = {
                        isPurchasing = true
                        error = null
                        scope.launch {
                            when (val result = billingManager.restorePurchases()) {
                                PremiumPurchaseResult.Success -> onPurchaseSuccess()
                                PremiumPurchaseResult.Cancelled -> isPurchasing = false
                                PremiumPurchaseResult.Pending -> {
                                    error = paymentPendingMessage
                                    isPurchasing = false
                                }
                                is PremiumPurchaseResult.Error -> {
                                    error = result.message
                                    isPurchasing = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isPurchasing
                ) {
                    Text(stringResource(R.string.action_restore_purchases))
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

private fun getTriggerReasonText(feature: Feature): String = when (feature) {
    Feature.FavoritesUnlimited -> "Limit of 3 favorite stations reached on Free plan."
    Feature.RouteSearchUnlimited -> "Limit of 1 route search per day reached on Free plan."
    Feature.MultiVehicle -> "Multi-vehicle garage management requires Premium."
    Feature.MaintenanceExport -> "Exporting maintenance logs to CSV requires Premium."
    Feature.PriceAlerts -> "Price drop notifications require Premium."
    Feature.AutoFullMap -> "Android Auto interactive map requires Premium."
    Feature.AutoStationList -> "Unlimited station search results on Android Auto require Premium."
    Feature.FullForecast -> "Full price forecast and Brent history require Premium."
    Feature.EvRealtimeAvailability -> "Real-time EV plug availability requires Premium."
    Feature.ParkingAlertsAndHistory -> "Parking history and location alerts require Premium."
    Feature.FuelCards -> "Fleet and fuel card management requires Premium."
    Feature.NoAds -> "Upgrade to Premium to remove all banner ads."
    Feature.FullRadarAlerts -> "Advanced safety zone alerts require Premium."
}

@Composable
private fun PremiumFeatureItem(title: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(text = description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
