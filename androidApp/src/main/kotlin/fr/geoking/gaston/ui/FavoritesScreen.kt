package fr.geoking.gaston.ui

import fr.geoking.gaston.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.api.geocoding.GeocodedPlace
import fr.geoking.gaston.community.FavoritesRepository
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.premium.BillingManager
import fr.geoking.gaston.ui.components.PremiumPaywallPopup
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    favoritesRepo: FavoritesRepository,
    settingsManager: SettingsManager,
    billingManager: BillingManager = koinInject(),
    onBack: () -> Unit,
    onSelectPoi: (Poi) -> Unit,
    onSelectLocation: (GeocodedPlace) -> Unit
) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    val settings by settingsManager.settings.collectAsState()

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
        return
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Stations", "Locations")

    var favoritePois by remember { mutableStateOf<List<Poi>>(emptyList()) }

    LaunchedEffect(Unit) {
        favoritePois = favoritesRepo.getFavorites()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cd_favorites)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> FavoritePoisList(
                    pois = favoritePois,
                    onSelect = onSelectPoi,
                    onRemove = { poi ->
                        scope.launch {
                            favoritesRepo.removeFavorite(poi.id)
                            favoritePois = favoritesRepo.getFavorites()
                        }
                    }
                )
                1 -> FavoriteLocationsList(
                    locations = settings.favoriteLocations,
                    onSelect = onSelectLocation,
                    onRemove = { loc ->
                        settingsManager.toggleFavoriteLocation(loc)
                    }
                )
            }
        }
    }
}

@Composable
private fun FavoritePoisList(
    pois: List<Poi>,
    onSelect: (Poi) -> Unit,
    onRemove: (Poi) -> Unit
) {
    if (pois.isEmpty()) {
        EmptyFavoritesHint("No favorite stations yet.")
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(pois, key = { it.id }) { poi ->
                ListItem(
                    modifier = Modifier.clickable { onSelect(poi) },
                    headlineContent = { Text(poi.name) },
                    supportingContent = { Text(poi.address) },
                    leadingContent = {
                        Icon(
                            if (poi.isElectric) Icons.Default.EvStation else Icons.Default.LocalGasStation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { onRemove(poi) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_remove), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun FavoriteLocationsList(
    locations: List<GeocodedPlace>,
    onSelect: (GeocodedPlace) -> Unit,
    onRemove: (GeocodedPlace) -> Unit
) {
    if (locations.isEmpty()) {
        EmptyFavoritesHint("No favorite locations yet.")
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(locations, key = { "${it.latitude},${it.longitude}" }) { loc ->
                ListItem(
                    modifier = Modifier.clickable { onSelect(loc) },
                    headlineContent = { Text(loc.label) },
                    supportingContent = { Text("%.4f, %.4f".format(loc.latitude, loc.longitude)) },
                    leadingContent = {
                        Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { onSelect(loc) }) {
                                Icon(Icons.Default.Directions, contentDescription = stringResource(R.string.cd_route), tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { onRemove(loc) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_remove), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun EmptyFavoritesHint(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
