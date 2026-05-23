package fr.geoking.gaston.ui

import fr.geoking.gaston.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.geoking.gaston.feature.location.LocationHelper
import fr.geoking.gaston.repository.FuelForecastRepository
import fr.geoking.gaston.repository.FuelForecastUiState
import fr.geoking.gaston.BuildConfig
import fr.geoking.gaston.ui.components.AdMobBanner
import fr.geoking.gaston.ui.components.FuelFilterChip
import fr.geoking.gaston.ui.components.FuelForecastChartCard
import fr.geoking.gaston.ui.components.UnifiedFuelForecastChartCard
import fr.geoking.gaston.ui.dashboard.GastonTheme
import fr.geoking.gaston.premium.BillingManager
import fr.geoking.gaston.ui.components.PremiumPaywallPopup
import fr.geoking.gaston.SettingsManager
import org.koin.compose.koinInject
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelForecastScreen(
    repository: FuelForecastRepository,
    onBack: () -> Unit,
    settingsManager: SettingsManager = koinInject(),
    billingManager: BillingManager = koinInject(),
    showAds: Boolean = false
) {
    val settings by settingsManager.settings.collectAsState()
    val scope = rememberCoroutineScope()

    var showPaywall by remember { mutableStateOf(!settings.hasPremiumFeatures) }
    if (showPaywall && !settings.hasPremiumFeatures) {
        PremiumPaywallPopup(
            billingManager = billingManager,
            onDismiss = onBack,
            onPurchaseSuccess = {
                scope.launch {
                    billingManager.refreshStatus()
                    settingsManager.setPremium(billingManager.isPremium.value)
                    showPaywall = false
                }
            }
        )
    }

    val context = LocalContext.current
    var states by remember { mutableStateOf<Map<String, FuelForecastUiState>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }

    val allFuelIds = setOf("gazole", "sp95", "sp98", "gplc", "e85")
    var selectedFuelIds by remember { mutableStateOf(setOf("gazole", "sp95", "sp98")) }

    LaunchedEffect(refreshTick) {
        isLoading = true
        try {
            val loc = withContext(Dispatchers.IO) { LocationHelper.getCurrentLocation(context) }
            if (loc != null) {
                states = repository.refreshAndBuildMultiUiState(loc.latitude, loc.longitude, allFuelIds)
            }
        } catch (e: Exception) {
            android.util.Log.e("FuelForecastScreen", "Failed to refresh forecasts", e)
        } finally {
            isLoading = false
        }
    }

    GastonTheme(themeMode = settings.uiThemeMode) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.screen_price_estimation)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { refreshTick++ }, enabled = !isLoading) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                        }
                    }
                )
            },
            bottomBar = {
                if (showAds) {
                    AdMobBanner(
                        adUnitId = BuildConfig.ADMOB_BANNER_ID,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        ) { padding ->
            if (isLoading && states.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(
                            stringResource(R.string.forecast_regional_estimations),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    item {
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            allFuelIds.forEach { fuelId ->
                                val label = when (fuelId) {
                                    "gazole" -> stringResource(R.string.fuel_gazole)
                                    "sp95" -> stringResource(R.string.fuel_sp95)
                                    "sp98" -> stringResource(R.string.fuel_sp98)
                                    "gplc" -> stringResource(R.string.fuel_gplc)
                                    "e85" -> stringResource(R.string.fuel_e85)
                                    else -> fuelId
                                }
                                FuelFilterChip(
                                    id = fuelId,
                                    label = label,
                                    isSelected = selectedFuelIds.contains(fuelId),
                                    onClick = {
                                        selectedFuelIds = if (selectedFuelIds.contains(fuelId)) {
                                            if (selectedFuelIds.size > 1) selectedFuelIds - fuelId else selectedFuelIds
                                        } else {
                                            selectedFuelIds + fuelId
                                        }
                                    }
                                )
                            }
                        }
                    }

                    item {
                        UnifiedFuelForecastChartCard(
                            states = states,
                            selectedFuelIds = selectedFuelIds,
                            isLoading = isLoading && states.isEmpty()
                        )
                    }

                    item {
                        Box(Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
