package fr.geoking.gaston.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import fr.geoking.gaston.MapEngine
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.api.belib.BorneAvailabilityProviderFactory
import fr.geoking.gaston.api.traffic.TrafficProviderFactory
import fr.geoking.gaston.community.CommunityPoiRepository
import fr.geoking.gaston.community.FavoritesRepository
import fr.geoking.gaston.api.geocoding.GeocodingClient
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.shared.diagnostics.DiagnosticStore
import fr.geoking.gaston.ui.MapScreen
import com.google.android.gms.maps.model.LatLng
import fr.geoking.gaston.ui.anim.AnimationPalette
import fr.geoking.gaston.ui.map.maplibre.VectorMapScreen

@Composable
fun MapFactory(
    poiProvider: PoiProvider,
    availabilityProviderFactory: BorneAvailabilityProviderFactory?,
    trafficProviderFactory: TrafficProviderFactory? = null,
    settingsManager: SettingsManager,
    authManager: fr.geoking.gaston.feature.auth.GoogleAuthManager?,
    diagnostics: DiagnosticStore,
    palette: AnimationPalette,
    onBack: () -> Unit,
    onPlanRoute: (() -> Unit)? = null,
    geocodingClient: GeocodingClient? = null,
    communityRepo: CommunityPoiRepository? = null,
    favoritesRepo: FavoritesRepository? = null,
    initialSelectedPoi: Poi? = null,
    initialCenter: LatLng? = null,
    initialZoom: Float? = null,
    showAds: Boolean = false
) {
    val settings by settingsManager.settings.collectAsState()
    when (settings.phoneMapEngine) {
        MapEngine.MapLibre -> VectorMapScreen(
            poiProvider = poiProvider,
            availabilityProviderFactory = availabilityProviderFactory,
            trafficProviderFactory = trafficProviderFactory,
            settingsManager = settingsManager,
            authManager = authManager,
            diagnostics = diagnostics,
            palette = palette,
            onBack = onBack,
            onPlanRoute = onPlanRoute,
            geocodingClient = geocodingClient,
            communityRepo = communityRepo,
            favoritesRepo = favoritesRepo,
            initialSelectedPoi = initialSelectedPoi,
            initialCenter = initialCenter,
            initialZoom = initialZoom,
            showAds = showAds
        )
        MapEngine.Google -> MapScreen(
            poiProvider = poiProvider,
            availabilityProviderFactory = availabilityProviderFactory,
            trafficProviderFactory = trafficProviderFactory,
            settingsManager = settingsManager,
            authManager = authManager,
            diagnostics = diagnostics,
            palette = palette,
            onBack = onBack,
            onPlanRoute = onPlanRoute,
            geocodingClient = geocodingClient,
            communityRepo = communityRepo,
            favoritesRepo = favoritesRepo,
            initialSelectedPoi = initialSelectedPoi,
            initialCenter = initialCenter,
            initialZoom = initialZoom,
            showAds = showAds
        )
        MapEngine.Custom -> SurfaceCustomMapScreen(
            poiProvider = poiProvider,
            availabilityProviderFactory = availabilityProviderFactory,
            trafficProviderFactory = trafficProviderFactory,
            settingsManager = settingsManager,
            authManager = authManager,
            diagnostics = diagnostics,
            palette = palette,
            onBack = onBack,
            onPlanRoute = onPlanRoute,
            geocodingClient = geocodingClient,
            communityRepo = communityRepo,
            favoritesRepo = favoritesRepo,
            initialSelectedPoi = initialSelectedPoi,
            initialCenter = initialCenter,
            initialZoom = initialZoom,
            showAds = showAds
        )
        MapEngine.Mapsforge -> SurfaceMapsforgeMapScreen(
            poiProvider = poiProvider,
            availabilityProviderFactory = availabilityProviderFactory,
            trafficProviderFactory = trafficProviderFactory,
            settingsManager = settingsManager,
            authManager = authManager,
            diagnostics = diagnostics,
            palette = palette,
            onBack = onBack,
            onPlanRoute = onPlanRoute,
            geocodingClient = geocodingClient,
            communityRepo = communityRepo,
            favoritesRepo = favoritesRepo,
            initialSelectedPoi = initialSelectedPoi,
            initialCenter = initialCenter,
            initialZoom = initialZoom,
            showAds = showAds
        )
    }
}

