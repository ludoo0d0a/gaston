package fr.geoking.gaston.ui.map

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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

    LaunchedEffect(initialSelectedPoi) {
        if (initialSelectedPoi != null) {
            sheetState.show()
        }
    }

    LaunchedEffect(selectedPoi, poisForOverlay, favoriteIds) {
        val sel = selectedPoi
        if (sel != null) {
            if (frozenPoisForSheet.isEmpty()) {
                val currentPois = poisForOverlay

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

    // Keep the map centered on the currently-selected POI (after sheet snap / scroll).
    LaunchedEffect(selectedPoi?.id, scrollRequestPoiId) {
        val poi = selectedPoi ?: return@LaunchedEffect
        if (scrollRequestPoiId != null) return@LaunchedEffect
        onCenterMapOnPoi(poi)
    }

    if (selectedPoi != null) {
        val listToShow = frozenPoisForSheet.takeIf { it.isNotEmpty() } ?: listOf(selectedPoi)
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
            containerColor = Color(0xFF1E293B),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.7f)) }
        ) {
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
                        modifier = Modifier.width(LocalConfiguration.current.screenWidthDp.dp),
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
                                if (settings.isPremium) {
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
                    if (settings.isPremium) {
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

