package fr.geoking.gaston.ui.map

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PriceCheck
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.poi.MapPoiFilter
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.community.CommunityPoiRepository
import fr.geoking.gaston.community.FavoritesRepository
import fr.geoking.gaston.community.isCommunityPoiId
import fr.geoking.gaston.effectiveIrvePowerLevels
import fr.geoking.gaston.effectiveMapEnergyFilterIds
import fr.geoking.gaston.intent.IntentNavigationHelper
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.premium.BillingManager
import fr.geoking.gaston.shared.location.approxDistanceKm
import fr.geoking.gaston.ui.components.PremiumPaywallPopup
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import org.koin.compose.koinInject

import androidx.compose.runtime.snapshotFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoiOverlayHost(
    context: Context,
    settingsManager: SettingsManager,
    settings: fr.geoking.gaston.AppSettings,
    availabilityByPoiId: Map<String, StationAvailabilitySummary>,
    favoritesRepo: FavoritesRepository?,
    favoriteIds: Set<String>,
    setFavoriteIds: (Set<String>) -> Unit,
    communityRepo: CommunityPoiRepository?,
    selectedPoi: Poi?,
    onSelectedPoiChange: (Poi?) -> Unit,
    poisForOverlay: List<Poi>,
    onCenterMapOnPoi: (poi: Poi) -> Unit,
    onInvalidate: () -> Unit,
    initialSelectedPoi: Poi? = null
) {
    val scope = rememberCoroutineScope()
    val billingManager: BillingManager = koinInject()

    var showPaywallForFavorite by remember { mutableStateOf(false) }
    var frozenPoisForSheet by remember { mutableStateOf<List<Poi>>(emptyList()) }
    var showCheapestOnly by remember { mutableStateOf(false) }
    var scrollRequestPoiId by remember { mutableStateOf(initialSelectedPoi?.id) }
    var poiForDetailsDialog by remember { mutableStateOf<Poi?>(null) }

    var showAddPoiSheet by remember { mutableStateOf(false) }
    var addPoiLinkedOfficialId by remember { mutableStateOf<String?>(null) }
    var addPoiInitialName by remember { mutableStateOf("") }
    var addPoiInitialAddress by remember { mutableStateOf("") }
    var addPoiInitialLat by remember { mutableStateOf<Double?>(null) }
    var addPoiInitialLng by remember { mutableStateOf<Double?>(null) }
    var addPoiExistingCommunityId by remember { mutableStateOf<String?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val lazyListState = rememberLazyListState()

    if (showPaywallForFavorite && !settings.hasPremiumFeatures) {
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

    LaunchedEffect(initialSelectedPoi) {
        if (initialSelectedPoi != null) {
            sheetState.show()
        }
    }

    LaunchedEffect(selectedPoi, poisForOverlay, favoriteIds) {
        val sel = selectedPoi
        if (sel != null) {
            val currentPois = poisForOverlay
            val shouldRebuild =
                frozenPoisForSheet.isEmpty() || currentPois.size > frozenPoisForSheet.size
            if (shouldRebuild) {
                val others = currentPois.filter { it.id != sel.id }.toMutableList()
                val sorted = mutableListOf(sel)

                var current: Poi = sel
                while (others.isNotEmpty()) {
                    val next = others.minBy { p ->
                        approxDistanceKm(current.latitude, current.longitude, p.latitude, p.longitude)
                    }
                    sorted.add(next)
                    others.remove(next)
                    current = next
                }

                frozenPoisForSheet = sorted
            }
        } else {
            frozenPoisForSheet = emptyList()
        }
    }

    val fuelIdsForCheapest = remember(settings) { settings.effectiveMapEnergyFilterIds() - "electric" }
    val minPrice = remember(poisForOverlay, fuelIdsForCheapest) {
        if (fuelIdsForCheapest.isEmpty()) null
        else {
            poisForOverlay.mapNotNull { poi ->
                poi.fuelPrices?.filter { !it.outOfStock && MapPoiFilter.fuelNameToId(it.fuelName) in fuelIdsForCheapest }
                    ?.minByOrNull { it.price }?.price
            }.minOrNull()
        }
    }

    val cheapestPois = remember(poisForOverlay, minPrice, fuelIdsForCheapest) {
        if (minPrice == null) emptyList()
        else {
            poisForOverlay.filter { poi ->
                poi.fuelPrices?.any { !it.outOfStock && MapPoiFilter.fuelNameToId(it.fuelName) in fuelIdsForCheapest && it.price == minPrice } == true
            }
        }
    }

    LaunchedEffect(showCheapestOnly) {
        if (showCheapestOnly && cheapestPois.isNotEmpty()) {
            if (selectedPoi == null || cheapestPois.none { it.id == selectedPoi.id }) {
                val first = cheapestPois.first()
                onSelectedPoiChange(first)
                scrollRequestPoiId = first.id
            }
        }
    }

    LaunchedEffect(selectedPoi?.id) {
        if (selectedPoi == null) {
            showCheapestOnly = false
        } else if (showCheapestOnly && cheapestPois.isNotEmpty() && cheapestPois.none { it.id == selectedPoi.id }) {
            showCheapestOnly = false
        }
    }

    // Keep the map centered on the currently-selected POI (after sheet snap / scroll).
    LaunchedEffect(selectedPoi?.id, scrollRequestPoiId) {
        val poi = selectedPoi ?: return@LaunchedEffect
        if (scrollRequestPoiId != null) return@LaunchedEffect
        onCenterMapOnPoi(poi)
    }

    if (selectedPoi != null) {
        val listToShow = if (showCheapestOnly && cheapestPois.isNotEmpty()) {
            cheapestPois
        } else {
            frozenPoisForSheet.takeIf { it.isNotEmpty() } ?: listOf(selectedPoi)
        }
        val currentListToShow by rememberUpdatedState(listToShow)

        LaunchedEffect(scrollRequestPoiId) {
            val requestId = scrollRequestPoiId ?: return@LaunchedEffect
            val index = currentListToShow.indexOfFirst { it.id == requestId }
            if (index >= 0) lazyListState.scrollToItem(index)
            scrollRequestPoiId = null
        }

        val currentScrollRequestPoiId by rememberUpdatedState(scrollRequestPoiId)
        LaunchedEffect(lazyListState) {
            snapshotFlow { lazyListState.isScrollInProgress }.collect { inProgress ->
                if (inProgress) return@collect
                if (currentScrollRequestPoiId != null) return@collect

                val viewportWidth = lazyListState.layoutInfo.viewportSize.width
                if (viewportWidth <= 0) return@collect

                val viewportCenter = viewportWidth / 2
                val closestItem = lazyListState.layoutInfo.visibleItemsInfo.minByOrNull { item ->
                    val itemCenter = item.offset + item.size / 2
                    kotlin.math.abs(itemCenter - viewportCenter)
                }

                val centeredPoi = closestItem?.index?.let { idx -> currentListToShow.getOrNull(idx) } ?: return@collect
                if (selectedPoi.id != centeredPoi.id) {
                    onSelectedPoiChange(centeredPoi)
                }
            }
        }

        ModalBottomSheet(
            onDismissRequest = {
                scope.launch { sheetState.hide() }
                onSelectedPoiChange(null)
                scrollRequestPoiId = null
            },
            sheetState = sheetState,
            sheetGesturesEnabled = true,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            if (minPrice != null && cheapestPois.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = { showCheapestOnly = !showCheapestOnly }) {
                        Icon(
                            imageVector = Icons.Default.PriceCheck,
                            contentDescription = "Show Cheapest",
                            tint = if (showCheapestOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            val configuration = LocalConfiguration.current
            val cardHeight = (configuration.screenHeightDp * 0.85f).dp
            LazyRow(
                state = lazyListState,
                flingBehavior = rememberSnapFlingBehavior(lazyListState = lazyListState),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(listToShow, key = { it.id }) { poi ->
                    val isFav = poi.id in favoriteIds
                    PoiDetailCard(
                        modifier = Modifier
                            .width(configuration.screenWidthDp.dp)
                            .height(cardHeight),
                        poi = poi,
                        availabilitySummary = availabilityByPoiId[poi.id],
                        highlightedFuelIds = settings.effectiveMapEnergyFilterIds(),
                        highlightedPowerLevels = settings.effectiveIrvePowerLevels(),
                        onNavigate = {
                            val uri = IntentNavigationHelper.getNavigationUri(poi)
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        },
                        onLocate = { onCenterMapOnPoi(poi) },
                        onShowDetails = { poiForDetailsDialog = poi },
                        isSelected = poi.id == selectedPoi.id,
                        isLoggedIn = settings.isLoggedIn,
                        isFavorite = isFav,
                        onToggleFavorite = if (settings.isLoggedIn && favoritesRepo != null) {
                            {
                                if (settings.hasPremiumFeatures) {
                                    scope.launch {
                                        favoritesRepo.toggleFavorite(poi)
                                        setFavoriteIds(favoritesRepo.getFavorites().map { it.id }.toSet())
                                    }
                                } else {
                                    showPaywallForFavorite = true
                                }
                            }
                        } else null
                    )
                }
            }
        }
    }

    if (showAddPoiSheet) {
        AddPoiSheet(
            initialLat = addPoiInitialLat,
            initialLng = addPoiInitialLng,
            linkedOfficialId = addPoiLinkedOfficialId,
            existingCommunityId = addPoiExistingCommunityId,
            initialName = addPoiInitialName,
            initialAddress = addPoiInitialAddress,
            communityRepo = communityRepo,
            onDismiss = { showAddPoiSheet = false },
            onSaved = { onInvalidate() }
        )
    }

    poiForDetailsDialog?.let { poi ->
        val ratingState = remember(poi.id) { mutableStateOf(settingsManager.getPoiRating(poi.id)) }
        PoiDetailsFullscreenDialog(
            poi = poi,
            availabilitySummary = availabilityByPoiId[poi.id],
            highlightedFuelIds = settings.effectiveMapEnergyFilterIds(),
            highlightedPowerLevels = settings.effectiveIrvePowerLevels(),
            rating = ratingState.value,
            onRate = { r ->
                settingsManager.setPoiRating(poi.id, r)
                ratingState.value = r
            },
            isLoggedIn = settings.isLoggedIn,
            isCommunityPoi = isCommunityPoiId(poi.id),
            isFavorite = poi.id in favoriteIds,
            onToggleFavorite = if (settings.isLoggedIn && favoritesRepo != null) {
                {
                    if (settings.hasPremiumFeatures) {
                        scope.launch {
                            favoritesRepo.toggleFavorite(poi)
                            setFavoriteIds(favoritesRepo.getFavorites().map { it.id }.toSet())
                        }
                    } else {
                        showPaywallForFavorite = true
                    }
                }
            } else null,
            onNavigate = {
                val uri = IntentNavigationHelper.getNavigationUri(poi)
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            },
            onEdit = if (settings.isLoggedIn && isCommunityPoiId(poi.id) && communityRepo != null) {
                {
                    addPoiExistingCommunityId = poi.id
                    addPoiInitialName = poi.name
                    addPoiInitialAddress = poi.address
                    addPoiInitialLat = poi.latitude
                    addPoiInitialLng = poi.longitude
                    addPoiLinkedOfficialId = null
                    showAddPoiSheet = true
                    poiForDetailsDialog = null
                    onSelectedPoiChange(null)
                    scope.launch { sheetState.hide() }
                }
            } else null,
            onRemove = if (settings.isLoggedIn && isCommunityPoiId(poi.id) && communityRepo != null) {
                {
                    scope.launch {
                        communityRepo.removeCommunityPoi(poi.id)
                        onInvalidate()
                        poiForDetailsDialog = null
                        onSelectedPoiChange(null)
                        sheetState.hide()
                    }
                }
            } else null,
            onHide = if (settings.isLoggedIn && !isCommunityPoiId(poi.id) && communityRepo != null) {
                {
                    scope.launch {
                        communityRepo.hideOfficialPoi(poi.id)
                        onInvalidate()
                        poiForDetailsDialog = null
                        onSelectedPoiChange(null)
                        sheetState.hide()
                    }
                }
            } else null,
            onSuggestCorrection = if (settings.isLoggedIn && !isCommunityPoiId(poi.id) && communityRepo != null) {
                {
                    addPoiLinkedOfficialId = poi.id
                    addPoiExistingCommunityId = null
                    addPoiInitialName = poi.name
                    addPoiInitialAddress = poi.address
                    addPoiInitialLat = poi.latitude
                    addPoiInitialLng = poi.longitude
                    showAddPoiSheet = true
                    poiForDetailsDialog = null
                    onSelectedPoiChange(null)
                    scope.launch { sheetState.hide() }
                }
            } else null,
            onDismiss = { poiForDetailsDialog = null }
        )
    }
}

