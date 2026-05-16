package fr.geoking.gaston.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import fr.geoking.gaston.feature.location.LocationHelper
import fr.geoking.gaston.intent.IntentNavigationHelper
import fr.geoking.gaston.intent.NavDestination
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.VehicleType
import fr.geoking.gaston.effectiveIrvePowerLevels
import fr.geoking.gaston.effectiveMapEnergyFilterIds
import fr.geoking.gaston.effectiveProviders
import fr.geoking.gaston.effectiveProvidersAt
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.BuildConfig
import fr.geoking.gaston.ui.components.AdMobBanner
import fr.geoking.gaston.ui.map.PoiMarkerHelper
import fr.geoking.gaston.ui.components.EnergyTypeSelectorRows
import fr.geoking.gaston.api.routing.RoutePlanner
import fr.geoking.gaston.api.routing.RoutingClient
import fr.geoking.gaston.toll.TollCalculator
import fr.geoking.gaston.toll.TollEstimate
import fr.geoking.gaston.api.traffic.TrafficInfo
import fr.geoking.gaston.api.traffic.TrafficProviderFactory
import fr.geoking.gaston.api.traffic.TrafficRequest
import fr.geoking.gaston.api.geocoding.GeocodingClient
import fr.geoking.gaston.api.geocoding.GeocodedPlace
import fr.geoking.gaston.premium.BillingManager
import fr.geoking.gaston.ui.components.PremiumPaywallPopup
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutePlanningScreen(
    routePlanner: RoutePlanner,
    routingClient: RoutingClient,
    tollCalculator: TollCalculator,
    trafficProviderFactory: TrafficProviderFactory? = null,
    poiProvider: PoiProvider,
    geocodingClient: GeocodingClient,
    settingsManager: SettingsManager,
    billingManager: fr.geoking.gaston.premium.BillingManager = org.koin.compose.koinInject(),
    onBack: () -> Unit,
    onShowOnMap: ((fr.geoking.gaston.api.routing.RouteResult, List<Poi>) -> Unit)? = null,
    onSearchAtLocation: ((Double, Double) -> Unit)? = null,
    initialDestination: NavDestination? = null,
    showAds: Boolean = false
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var currentRoute by remember { mutableStateOf<fr.geoking.gaston.api.routing.RouteResult?>(null) }
    var originQuery by remember { mutableStateOf("") }
    var destQuery by remember(initialDestination) {
        mutableStateOf(
            if (initialDestination != null) {
                initialDestination.address ?: initialDestination.latitude?.let { "${initialDestination.latitude}, ${initialDestination.longitude}" } ?: ""
            } else ""
        )
    }
    var useCurrentLocationAsOrigin by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var stations by remember { mutableStateOf<List<Poi>>(emptyList()) }
    var tollEstimate by remember { mutableStateOf<TollEstimate?>(null) }
    var routeTraffic by remember { mutableStateOf<TrafficInfo?>(null) }
    var calculateTrigger by remember { mutableStateOf(0) }

    val settings by settingsManager.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var showPaywallForFavorite by remember { mutableStateOf(false) }

    if (showPaywallForFavorite && !settings.isPremium) {
        PremiumPaywallPopup(
            billingManager = billingManager,
            onDismiss = { showPaywallForFavorite = false },
            onPurchaseSuccess = {
                scope.launch {
                    billingManager.refreshStatus()
                    settingsManager.setPremium(billingManager.isPremium.value)
                    showPaywallForFavorite = false
                }
            }
        )
    }

    var originSuggestions by remember { mutableStateOf<List<GeocodedPlace>>(emptyList()) }
    var destSuggestions by remember { mutableStateOf<List<GeocodedPlace>>(emptyList()) }
    var originFocused by remember { mutableStateOf(false) }
    var destFocused by remember { mutableStateOf(false) }
    var originFieldHeight by remember { mutableStateOf(0) }
    var destFieldHeight by remember { mutableStateOf(0) }
    var selectedOrigin by remember { mutableStateOf<GeocodedPlace?>(null) }
    var selectedDest by remember { mutableStateOf<GeocodedPlace?>(null) }

    LaunchedEffect(originQuery, settings.favoriteLocations) {
        if (originQuery.isBlank() || useCurrentLocationAsOrigin) {
            originSuggestions = emptyList()
            return@LaunchedEffect
        }
        val historyMatches = settings.routeHistory.filter { it.label.contains(originQuery, ignoreCase = true) }
        val favoriteMatches = settings.favoriteLocations.filter { it.label.contains(originQuery, ignoreCase = true) }
        originSuggestions = (favoriteMatches + historyMatches).distinctBy { it.label }
        if (originQuery.length > 2) {
            delay(500)
            try {
                val remote = geocodingClient.geocode(
                    originQuery,
                    limit = 5,
                    biasLatitude = settings.lastKnownLat,
                    biasLongitude = settings.lastKnownLon
                )
                val newSuggestions = (favoriteMatches + historyMatches + remote).distinctBy { it.label }
                originSuggestions = newSuggestions
            } catch (e: Exception) {
                // Ignore geocoding errors for autocomplete
            }
        }
    }

    LaunchedEffect(destQuery, settings.favoriteLocations) {
        if (destQuery.isBlank()) {
            destSuggestions = emptyList()
            return@LaunchedEffect
        }
        val historyMatches = settings.routeHistory.filter { it.label.contains(destQuery, ignoreCase = true) }
        val favoriteMatches = settings.favoriteLocations.filter { it.label.contains(destQuery, ignoreCase = true) }
        destSuggestions = (favoriteMatches + historyMatches).distinctBy { it.label }
        if (destQuery.length > 2) {
            delay(500)
            try {
                val remote = geocodingClient.geocode(
                    destQuery,
                    limit = 5,
                    biasLatitude = settings.lastKnownLat,
                    biasLongitude = settings.lastKnownLon
                )
                val newSuggestions = (favoriteMatches + historyMatches + remote).distinctBy { it.label }
                destSuggestions = newSuggestions
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plan route") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showAds) {
                AdMobBanner(
                    adUnitId = BuildConfig.ADMOB_BANNER_ID,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("Origin", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Switch(
                    checked = useCurrentLocationAsOrigin,
                    onCheckedChange = { useCurrentLocationAsOrigin = it }
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    if (useCurrentLocationAsOrigin) "Use my current location" else "Enter an address / city",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (!useCurrentLocationAsOrigin) {
                Spacer(modifier = Modifier.height(8.dp))
                Box {
                    OutlinedTextField(
                        value = originQuery,
                        onValueChange = {
                            originQuery = it.take(120)
                            selectedOrigin = null
                        },
                        placeholder = { Text("Origin address or city") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.fillMaxWidth()
                            .onFocusChanged { originFocused = it.isFocused }
                            .onSizeChanged { originFieldHeight = it.height },
                        shape = RoundedCornerShape(24.dp),
                        leadingIcon = {
                            Icon(
                                Icons.Default.Place,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (originQuery.isNotEmpty()) {
                                    IconButton(onClick = {
                                        originQuery = ""
                                        selectedOrigin = null
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                                val place = selectedOrigin
                                if (place != null) {
                                    val isFavorite = settings.favoriteLocations.any { it.latitude == place.latitude && it.longitude == place.longitude }
                                    IconButton(onClick = {
                                        if (settings.isPremium) {
                                            settingsManager.toggleFavoriteLocation(place)
                                        } else {
                                            showPaywallForFavorite = true
                                        }
                                    }) {
                                        Icon(
                                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                                                tint = if (isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Transparent,
                        )
                    )
                    if (originFocused && originSuggestions.isNotEmpty()) {
                        Popup(
                            onDismissRequest = { originFocused = false },
                            offset = IntOffset(0, originFieldHeight),
                            properties = PopupProperties(focusable = false)
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(8.dp)
                            ) {
                                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                                    items(originSuggestions) { suggestion ->
                                        val isHistory = settings.routeHistory.any { it.label == suggestion.label && it.latitude == suggestion.latitude && it.longitude == suggestion.longitude }
                                        val isFavorite = settings.favoriteLocations.any { it.label == suggestion.label && it.latitude == suggestion.latitude && it.longitude == suggestion.longitude }
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                originQuery = suggestion.label
                                                selectedOrigin = suggestion
                                                originFocused = false
                                            }.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                when {
                                                    isFavorite -> Icons.Default.Star
                                                    isHistory -> Icons.Default.History
                                                    else -> Icons.Default.Place
                                                },
                                                contentDescription = null,
                                                tint = if (isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Text(
                                                text = suggestion.label,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (!isFavorite && (isHistory || suggestion.latitude != 0.0)) {
                                                IconButton(
                                                    onClick = {
                                                        if (settings.isPremium) {
                                                            settingsManager.toggleFavoriteLocation(suggestion)
                                                        } else {
                                                            showPaywallForFavorite = true
                                                        }
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.StarBorder, contentDescription = "Add to favorites", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                                                }
                                            } else if (isFavorite) {
                                                IconButton(
                                                    onClick = {
                                                        if (settings.isPremium) {
                                                            settingsManager.toggleFavoriteLocation(suggestion)
                                                        } else {
                                                            showPaywallForFavorite = true
                                                        }
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.Star, contentDescription = "Remove from favorites", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Destination", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Box {
                OutlinedTextField(
                    value = destQuery,
                    onValueChange = {
                        destQuery = it.take(120)
                        selectedDest = null
                    },
                    placeholder = { Text("Destination address or city") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                        .onFocusChanged { destFocused = it.isFocused }
                        .onSizeChanged { destFieldHeight = it.height },
                    shape = RoundedCornerShape(24.dp),
                    leadingIcon = {
                        Icon(
                            Icons.Default.Directions,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (destQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    destQuery = ""
                                    selectedDest = null
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                            val place = selectedDest
                            if (place != null) {
                                val isFavorite = settings.favoriteLocations.any { it.latitude == place.latitude && it.longitude == place.longitude }
                                IconButton(onClick = {
                                    if (settings.isPremium) {
                                        settingsManager.toggleFavoriteLocation(place)
                                    } else {
                                        showPaywallForFavorite = true
                                    }
                                }) {
                                    Icon(
                                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                                        tint = if (isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    )
                                }
                                if (onSearchAtLocation != null) {
                                    IconButton(onClick = { onSearchAtLocation(place.latitude, place.longitude) }) {
                                        Icon(Icons.Default.Place, contentDescription = "Search at destination", tint = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                    )
                )
                if (destFocused && destSuggestions.isNotEmpty()) {
                    Popup(
                        onDismissRequest = { destFocused = false },
                        offset = IntOffset(0, destFieldHeight),
                        properties = PopupProperties(focusable = false)
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(8.dp)
                        ) {
                            LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                                items(destSuggestions) { suggestion ->
                                    val isHistory = settings.routeHistory.any { it.label == suggestion.label && it.latitude == suggestion.latitude && it.longitude == suggestion.longitude }
                                val isFavorite = settings.favoriteLocations.any { it.label == suggestion.label && it.latitude == suggestion.latitude && it.longitude == suggestion.longitude }
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            destQuery = suggestion.label
                                            selectedDest = suggestion
                                            destFocused = false
                                        }.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            when {
                                                isFavorite -> Icons.Default.Star
                                                isHistory -> Icons.Default.History
                                                else -> Icons.Default.Place
                                            },
                                            contentDescription = null,
                                            tint = if (isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            text = suggestion.label,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (!isFavorite && (isHistory || suggestion.latitude != 0.0)) {
                                            IconButton(
                                                onClick = {
                                                    if (settings.isPremium) {
                                                        settingsManager.toggleFavoriteLocation(suggestion)
                                                    } else {
                                                        showPaywallForFavorite = true
                                                    }
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.StarBorder, contentDescription = "Add to favorites", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                                            }
                                        } else if (isFavorite) {
                                            IconButton(
                                                onClick = {
                                                    if (settings.isPremium) {
                                                        settingsManager.toggleFavoriteLocation(suggestion)
                                                    } else {
                                                        showPaywallForFavorite = true
                                                    }
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Star, contentDescription = "Remove from favorites", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        loading = true
                        error = null
                        stations = emptyList()
                        tollEstimate = null
                        routeTraffic = null
                        calculateTrigger++
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !loading && destQuery.isNotBlank() && (useCurrentLocationAsOrigin || originQuery.isNotBlank())
                ) {
                    Text(if (loading) "Calculating…" else "Calculate route")
                }

                if (currentRoute != null && onShowOnMap != null) {
                    Button(
                        onClick = { onShowOnMap(currentRoute!!, stations) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Show on Map")
                    }
                }
            }

            error?.let { err ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(err, color = Color(0xFFF87171), style = MaterialTheme.typography.bodySmall)
            }

            if (loading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            if (stations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                tollEstimate?.let { toll ->
                    Text(
                        "Estimated toll: €%.2f".format(toll.amountEur),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                routeTraffic?.let { info ->
                    val roadSummary = info.events.map { it.roadRef }.distinct().sorted().joinToString(", ")
                    Text(
                        "Traffic (${info.providerId}): ${info.events.size} events on $roadSummary",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                val settings by settingsManager.settings.collectAsState()
                val routeMidLat = stations.firstOrNull()?.latitude
                    ?: currentRoute?.points?.firstOrNull()?.first
                val routeMidLon = stations.firstOrNull()?.longitude
                    ?: currentRoute?.points?.firstOrNull()?.second
                val effectiveProviders = remember(settings, routeMidLat, routeMidLon) {
                    if (routeMidLat != null && routeMidLon != null) {
                        settings.effectiveProvidersAt(routeMidLat, routeMidLon)
                    } else {
                        settings.effectiveProviders()
                    }
                }
                val filteredStations = remember(stations, settings, effectiveProviders) {
                    fr.geoking.gaston.StationMapFilters.apply(
                        settings = settings,
                        pois = stations,
                        providers = effectiveProviders,
                        skipWhenOnlyOverpass = false
                    )
                }

                val energyTypes = settings.effectiveMapEnergyFilterIds()
                val fuelIdsForCheapest = energyTypes - "electric"
                val minPrice = remember(filteredStations, fuelIdsForCheapest) {
                    if (fuelIdsForCheapest.isEmpty()) null
                    else {
                        filteredStations.mapNotNull { poi ->
                            poi.fuelPrices?.filter { !it.outOfStock && fr.geoking.gaston.poi.MapPoiFilter.fuelNameToId(it.fuelName) in fuelIdsForCheapest }
                                ?.minByOrNull { it.price }?.price
                        }.minOrNull()
                    }
                }

                val recommendations = remember(filteredStations, settings, currentRoute) {
                    val route = currentRoute ?: return@remember emptyList<Poi>()
                    val rangeKm = when (settings.vehicleEnergy) {
                        "electric" -> {
                            val bat = settings.batteryCapacityKwh
                            val cons = settings.evConsumptionKwhPer100km
                            if (bat != null && cons != null && cons > 0) (bat / cons * 100.0)
                            else settings.evRangeKm.toDouble()
                        }
                        "hybrid" -> {
                            val gasCap = settings.gasTankCapacityLiters
                            val gasCons = settings.gasConsumptionLper100km
                            val gasRange = if (gasCap != null && gasCons != null && gasCons > 0) (gasCap / gasCons * 100.0) else 0.0

                            val bat = settings.batteryCapacityKwh
                            val eleCons = settings.evConsumptionKwhPer100km
                            val eleRange = if (bat != null && eleCons != null && eleCons > 0) (bat / eleCons * 100.0) else settings.evRangeKm.toDouble()

                            gasRange + eleRange
                        }
                        else -> { // gas
                            val cap = settings.gasTankCapacityLiters
                            val cons = settings.gasConsumptionLper100km
                            if (cap != null && cons != null && cons > 0) (cap / cons * 100.0) else 400.0
                        }
                    }

                    val result = mutableListOf<Poi>()
                    var currentRange = rangeKm
                    var lastPointIdx = 0

                    // Simple greedy algorithm: find stations when range is < 20%
                    // This is a basic suggestion based on distance along route
                    val points = route.points
                    var distAcc = 0.0
                    for (i in 1 until points.size) {
                        val p0 = points[i-1]
                        val p1 = points[i]
                        val d = fr.geoking.gaston.shared.location.haversineKm(p0.first, p0.second, p1.first, p1.second)
                        distAcc += d
                        if (distAcc >= rangeKm * 0.8) {
                            // Find best station near this point
                            val nearby = filteredStations.filter { poi ->
                                fr.geoking.gaston.shared.location.haversineKm(p1.first, p1.second, poi.latitude, poi.longitude) < 5.0
                            }.sortedBy { poi ->
                                // Prioritize cheapest if fuel, otherwise closest
                                if (minPrice != null) {
                                    poi.fuelPrices?.minByOrNull { it.price }?.price ?: 99.0
                                } else {
                                    fr.geoking.gaston.shared.location.haversineKm(p1.first, p1.second, poi.latitude, poi.longitude)
                                }
                            }
                            nearby.firstOrNull()?.let {
                                if (it !in result) {
                                    result.add(it)
                                    distAcc = 0.0 // Assume refuel only if a station was found
                                }
                            }
                        }
                    }
                    result
                }

                val title = if (settings.vehicleType == VehicleType.Truck || settings.vehicleType == VehicleType.Motorhome) {
                    "POIs along route (${filteredStations.size})"
                } else {
                    "Stations along route (${filteredStations.size})"
                }
                val listState = rememberLazyListState()
                Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                if (recommendations.isNotEmpty()) {
                    Text("Recommended stops", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(recommendations) { poi ->
                            val scope = rememberCoroutineScope()
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                modifier = Modifier.width(200.dp).clickable {
                                    val index = filteredStations.indexOfFirst { it.id == poi.id }
                                    if (index >= 0) {
                                        scope.launch { listState.animateScrollToItem(index) }
                                    }
                                }
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(poi.name, color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                    val price = poi.fuelPrices?.minByOrNull { it.price }?.price
                                    if (price != null) {
                                        Text("€%.2f".format(price), color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                EnergyTypeSelectorRows(
                    settings = settings,
                    settingsManager = settingsManager,
                    providers = effectiveProviders,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredStations, key = { it.id }) { poi ->
                        val isCheapest = minPrice != null && poi.fuelPrices?.any { !it.outOfStock && fr.geoking.gaston.poi.MapPoiFilter.fuelNameToId(it.fuelName) in fuelIdsForCheapest && it.price == minPrice } == true
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCheapest) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            border = if (isCheapest) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.secondary) else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(poi.name.ifBlank { poi.address }, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                        if (isCheapest) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.secondary,
                                                shape = MaterialTheme.shapes.extraSmall,
                                                modifier = Modifier.padding(start = 4.dp)
                                            ) {
                                                Text(
                                                    "CHEAPEST",
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    Text(poi.address, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)

                                    val energyTypes = settings.effectiveMapEnergyFilterIds()
                                    val powerLevels = settings.effectiveIrvePowerLevels()
                                    val label = PoiMarkerHelper.getPoiLabel(poi, energyTypes, powerLevels)

                                    if (label != null) {
                                        val color = PoiMarkerHelper.getPoiColor(
                                            poi,
                                            poi.poiCategory ?: if (poi.isElectric) fr.geoking.gaston.poi.PoiCategory.Irve else fr.geoking.gaston.poi.PoiCategory.Gas,
                                            energyTypes,
                                            powerLevels
                                        )
                                        Text(
                                            text = label,
                                            color = Color(color),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else if (poi.powerKw != null) {
                                        Text("${poi.powerKw} kW", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        val uri = IntentNavigationHelper.getNavigationUri(poi)
                                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    }
                                ) {
                                    Icon(Icons.Default.Directions, contentDescription = "Navigate", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(calculateTrigger, initialDestination) {
        if (calculateTrigger == 0 && initialDestination == null) return@LaunchedEffect
        loading = true
        error = null
        stations = emptyList()
        tollEstimate = null
        routeTraffic = null
        try {
            val origin = if (useCurrentLocationAsOrigin) {
                if (!hasLocation) {
                    loading = false
                    error = "Location permission is required to use current location"
                    return@LaunchedEffect
                }
                val loc = LocationHelper.getCurrentLocation(context)
                if (loc == null) {
                    loading = false
                    error = "Could not determine current location"
                    return@LaunchedEffect
                }
                Pair("Current location", loc.latitude to loc.longitude)
            } else {
                selectedOrigin?.let { it.label to (it.latitude to it.longitude) } ?: run {
                    val results = geocodingClient.geocode(originQuery, limit = 1)
                    val first = results.firstOrNull()
                    if (first == null) {
                        loading = false
                        error = "Origin not found"
                        return@LaunchedEffect
                    }
                    selectedOrigin = first
                    Pair(first.label, first.latitude to first.longitude)
                }
            }

            val destination = if (initialDestination?.latitude != null && initialDestination.longitude != null) {
                val resolved = GeocodedPlace(initialDestination.address ?: destQuery, initialDestination.latitude, initialDestination.longitude)
                selectedDest = resolved
                Pair(resolved.label, resolved.latitude to resolved.longitude)
            } else {
                selectedDest?.let { it.label to (it.latitude to it.longitude) } ?: run {
                    val destResults = geocodingClient.geocode(destQuery, limit = 1)
                    val destFirst = destResults.firstOrNull()
                    if (destFirst == null) {
                        loading = false
                        error = "Destination not found"
                        return@LaunchedEffect
                    }
                    selectedDest = destFirst
                    Pair(destFirst.label, destFirst.latitude to destFirst.longitude)
                }
            }

            val (oLat, oLon) = origin.second
            val (dLat, dLon) = destination.second

            if (!useCurrentLocationAsOrigin) {
                settingsManager.addRouteHistory(GeocodedPlace(origin.first, oLat, oLon))
            }
            settingsManager.addRouteHistory(GeocodedPlace(destination.first, dLat, dLon))

            val settings = settingsManager.settings.value
            val route = routingClient.getRoute(oLat, oLon, dLat, dLon)
            currentRoute = route
            if (route != null) {
                tollEstimate = tollCalculator.estimateToll(route.points, settings.vehicleType)
                val trafficProviders = trafficProviderFactory?.getProvidersForRoute(route.points).orEmpty()
                routeTraffic = trafficProviders.firstOrNull()?.let { provider ->
                    provider.getTraffic(TrafficRequest.Route(route.points))
                }

                routePlanner.getStationsAlongRouteFlow(
                    route.points,
                    poiProvider,
                    radiusMeters = settings.routeStationSearchRadiusMeters
                ).collect { incrementalStations ->
                    stations = incrementalStations
                    loading = false // Show results as soon as first batch arrives
                }
            } else {
                tollEstimate = null
                routeTraffic = null
                loading = false
                error = "No route found"
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            loading = false
            error = e.message ?: e.toString()
        }
    }
}
