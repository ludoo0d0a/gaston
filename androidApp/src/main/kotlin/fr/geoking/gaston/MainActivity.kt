package fr.geoking.gaston

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import fr.geoking.gaston.BuildConfig
import fr.geoking.gaston.shared.diagnostics.DiagnosticStore
import fr.geoking.gaston.shared.network.NetworkService
import fr.geoking.gaston.shared.network.NetworkStatus
import fr.geoking.gaston.di.MapDeps
import fr.geoking.gaston.di.MapModuleLoader
import fr.geoking.gaston.ui.MapScreen
import fr.geoking.gaston.ui.map.maplibre.VectorMapScreen
import fr.geoking.gaston.ui.map.maplibre.DirectionsMapScreen
import fr.geoking.gaston.ui.PhoneNetworkLocationScreen
import fr.geoking.gaston.ui.PhoneDashboardScreen
import fr.geoking.gaston.ui.PlaystoreTheme
import fr.geoking.gaston.ui.FavoritesScreen
import fr.geoking.gaston.ui.RoutePlanningScreen
import fr.geoking.gaston.api.routing.RouteResult
import fr.geoking.gaston.ui.SettingsScreen
import fr.geoking.gaston.ui.SettingsScreenPage
import fr.geoking.gaston.ui.FuelForecastScreen
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiProviderType
import fr.geoking.gaston.repository.FuelForecastRepository
import fr.geoking.gaston.ui.UpdateAvailableDialog
import fr.geoking.gaston.ui.anim.AnimationPalettes
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.install.model.InstallStatus
import fr.geoking.gaston.update.InAppUpdateHelper
import fr.geoking.gaston.feature.auth.GoogleAuthManager
import fr.geoking.gaston.intent.IntentNavigationHelper
import fr.geoking.gaston.intent.NavDestination
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.android.ext.android.get
import org.koin.android.ext.android.getKoin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    private val inAppUpdateHelper by lazy { InAppUpdateHelper(applicationContext) }
    private val mapDepsState = MutableStateFlow<MapDeps?>(null)
    private val pendingNavDestination = MutableStateFlow<NavDestination?>(null)
    /** Set from [handleIntent] when the host opens [gaston://map/libremap] (e.g. Android Auto lab). */
    private val pendingLibreMapLab = MutableStateFlow(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val nav = IntentNavigationHelper.parseNavIntent(intent)
        if (nav != null) {
            pendingNavDestination.value = nav
        }
        val data = intent.data
        if (data?.scheme == "gaston" && data.host == "map" && data.path == "/libremap") {
            pendingLibreMapLab.value = true
        }
    }

    private fun ensureMapDeps() {
        if (mapDepsState.value != null) return
        try {
            MapModuleLoader.ensureLoaded()
            mapDepsState.value = MapDeps(
                poiProvider = get(),
                availabilityProviderFactory = get(),
                communityRepo = get(),
                favoritesRepo = get(),
                trafficProviderFactory = get(),
                weatherProviderFactory = get(),
                routePlanner = get(),
                routingClient = get(),
                tollCalculator = get(),
                geocodingClient = get()
            )
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "ensureMapDeps: failed to load map dependencies", e)
        }
    }

    private val updateResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            // User cancelled or update failed; can check again later
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("MainActivity", "onCreate start")

        val appError = GastonApplication.initError
        if (appError != null) {
            android.util.Log.e("MainActivity", "Showing startup error (Koin failed)", appError)
            try {
                setContent { StartupErrorContent(appError) }
            } catch (ce: Throwable) {
                android.util.Log.e("MainActivity", "setContent failed for StartupErrorContent", ce)
            }
            return
        }

        handleIntent(intent)
        try {
            android.util.Log.d("MainActivity", "Resolving Koin dependencies...")
            val diagnostics: DiagnosticStore = get()
            val settingsManager: SettingsManager = get()
            val authManager: GoogleAuthManager? = getKoin().getOrNull()
            val networkService: NetworkService = get()
            val fuelForecastRepository: FuelForecastRepository = get()
            android.util.Log.d("MainActivity", "Dependencies resolved successfully.")

            android.util.Log.d("MainActivity", "Calling setContent...")
            installMainComposeContent(
                diagnostics = diagnostics,
                settingsManager = settingsManager,
                authManager = authManager,
                networkService = networkService,
                fuelForecastRepository = fuelForecastRepository,
                isPlaystoreDistribution = BuildConfig.IS_PLAYSTORE_DISTRIBUTION
            )
            android.util.Log.d("MainActivity", "setContent called successfully.")
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Startup failed", e)
            try {
                setContent { StartupErrorContent(e) }
            } catch (ce: Throwable) {
                android.util.Log.e("MainActivity", "setContent failed for StartupErrorContent (fallback)", ce)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Check on every open/resume to reliably prompt for updates.
        // Only meaningful for Play Store distribution.
        if (BuildConfig.IS_PLAYSTORE_DISTRIBUTION) {
            inAppUpdateHelper.checkForUpdate()
        }
    }

    private fun installMainComposeContent(
        diagnostics: DiagnosticStore,
        settingsManager: SettingsManager,
        authManager: GoogleAuthManager?,
        networkService: NetworkService,
        fuelForecastRepository: FuelForecastRepository,
        isPlaystoreDistribution: Boolean
    ) {
        try {
            setContent {
                MainActivityComposeRoot(
                    diagnostics = diagnostics,
                    settingsManager = settingsManager,
                    authManager = authManager,
                    mapDepsState = mapDepsState,
                    onRequestMapDeps = { ensureMapDeps() },
                    networkService = networkService,
                    fuelForecastRepository = fuelForecastRepository,
                    inAppUpdateHelper = inAppUpdateHelper,
                    updateResultLauncher = updateResultLauncher,
                    pendingNavDestination = pendingNavDestination,
                    pendingLibreMapLab = pendingLibreMapLab,
                    isPlaystoreDistribution = isPlaystoreDistribution
                )
            }
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "installMainComposeContent: setContent crashed", e)
            try {
                setContent { StartupErrorContent(e) }
            } catch (ce: Throwable) {
                android.util.Log.e("MainActivity", "installMainComposeContent: fallback setContent crashed", ce)
            }
        }
    }

    override fun onDestroy() {
        inAppUpdateHelper.unregister()
        super.onDestroy()
    }
}

@Composable
private fun MainActivityComposeRoot(
    diagnostics: DiagnosticStore,
    settingsManager: SettingsManager,
    authManager: GoogleAuthManager?,
    mapDepsState: kotlinx.coroutines.flow.MutableStateFlow<MapDeps?>,
    onRequestMapDeps: () -> Unit,
    networkService: NetworkService,
    fuelForecastRepository: FuelForecastRepository,
    inAppUpdateHelper: InAppUpdateHelper,
    updateResultLauncher: ActivityResultLauncher<IntentSenderRequest>,
    pendingNavDestination: MutableStateFlow<NavDestination?>,
    pendingLibreMapLab: MutableStateFlow<Boolean>,
    isPlaystoreDistribution: Boolean
) {
    android.util.Log.d("MainActivity", "Compose setContent block running")
    val settings by settingsManager.settings.collectAsState()

    LaunchedEffect(Unit) {
        android.util.Log.d("MainActivity", "Compose first frame")
    }

    LaunchedEffect(Unit) {
        if (settings.isLoggedIn) {
            settingsManager.triggerPullAndMerge()
        }
    }

    val context = LocalContext.current
    var hasLocationPermission by remember(context) {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val locationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> hasLocationPermission = isGranted }
    )

    val installStatus by inAppUpdateHelper.installStatus.collectAsState()
    val isUpdateInProgress = remember(installStatus) {
        installStatus == InstallStatus.PENDING ||
                installStatus == InstallStatus.DOWNLOADING ||
                installStatus == InstallStatus.INSTALLING
    }

    MainUI(
        diagnostics = diagnostics,
        settingsManager = settingsManager,
        authManager = authManager,
        mapDepsState = mapDepsState,
        onRequestMapDeps = onRequestMapDeps,
        networkService = networkService,
        fuelForecastRepository = fuelForecastRepository,
        inAppUpdateHelper = inAppUpdateHelper,
        onStartUpdate = { info -> inAppUpdateHelper.startUpdate(info, updateResultLauncher) },
        isUpdateInProgress = isUpdateInProgress,
        pendingNavDestinationFlow = pendingNavDestination,
        pendingLibreMapLab = pendingLibreMapLab,
        isPlaystoreDistribution = isPlaystoreDistribution,
        hasLocationPermission = hasLocationPermission,
        onRequestLocationPermission = {
            locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
    )
}

@Composable
fun MainUI(
    diagnostics: DiagnosticStore,
    settingsManager: SettingsManager,
    authManager: GoogleAuthManager?,
    mapDepsState: kotlinx.coroutines.flow.StateFlow<MapDeps?>,
    onRequestMapDeps: () -> Unit,
    networkService: NetworkService,
    fuelForecastRepository: FuelForecastRepository? = null,
    inAppUpdateHelper: InAppUpdateHelper? = null,
    onStartUpdate: (AppUpdateInfo) -> Unit = {},
    isUpdateInProgress: Boolean = false,
    pendingNavDestinationFlow: kotlinx.coroutines.flow.MutableStateFlow<NavDestination?>? = null,
    pendingLibreMapLab: MutableStateFlow<Boolean>? = null,
    isPlaystoreDistribution: Boolean = false,
    hasLocationPermission: Boolean = false,
    onRequestLocationPermission: () -> Unit = {}
) {
    val pendingNavFlow = pendingNavDestinationFlow ?: remember { MutableStateFlow<NavDestination?>(null) }
    val mapDeps by mapDepsState.collectAsState()
    val errorLog by diagnostics.errorLog.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    /** Both flavors: dashboard home; map opens from there or from deep link. */
    var showMap by remember { mutableStateOf(false) }
    var showNetworkDiagnostics by remember { mutableStateOf(false) }
    var showPlaystoreSettings by remember { mutableStateOf(false) }
    var playstoreSettingsInitialStack by remember { mutableStateOf<List<SettingsScreenPage>?>(null) }
    var showFavorites by remember { mutableStateOf(false) }
    var showRoutePlanning by remember { mutableStateOf(false) }
    var showDirectionsMap by remember { mutableStateOf(false) }
    var showFuelForecast by remember { mutableStateOf(false) }
    var pendingMapPoi by remember { mutableStateOf<Poi?>(null) }
    var pendingMapLocation by remember { mutableStateOf<com.google.android.gms.maps.model.LatLng?>(null) }
    var dashboardSelectedLocation by remember { mutableStateOf<fr.geoking.gaston.api.geocoding.GeocodedPlace?>(null) }

    LaunchedEffect(dashboardSelectedLocation) {
        dashboardSelectedLocation?.let {
            pendingMapLocation = com.google.android.gms.maps.model.LatLng(it.latitude, it.longitude)
        }
    }

    var routeForDirections by remember { mutableStateOf<RouteResult?>(null) }
    var stationsForDirections by remember { mutableStateOf<List<Poi>>(emptyList()) }
    var initialNavDestination by remember { mutableStateOf<NavDestination?>(null) }
    var settingsInitialStack by remember { mutableStateOf<List<SettingsScreenPage>?>(null) }

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        pendingNavFlow.collect { nav ->
            if (nav != null) {
                initialNavDestination = nav
                showRoutePlanning = true
                showMap = true
                pendingNavFlow.value = null
            }
        }
    }

    val libreMapLabFlow = pendingLibreMapLab ?: remember { MutableStateFlow(false) }
    LaunchedEffect(Unit) {
        libreMapLabFlow.collect { open ->
            if (open) {
                settingsManager.setPhoneMapEngine(MapEngine.MapLibre)
                showMap = true
                libreMapLabFlow.value = false
            }
        }
    }

    LaunchedEffect(Unit) {
        val intent = (context as? Activity)?.intent
        if (intent?.data?.scheme == "gaston" && intent.data?.host == "map") {
            val path = intent.data?.path
            val currentSettings = settingsManager.settings.value
            if (path == "/gas_stations") {
                if (currentSettings.useVehicleFilter && (currentSettings.vehicleEnergy == "gas" || currentSettings.vehicleEnergy == "hybrid")) {
                    // Already configured for car, just show map
                } else if (currentSettings.vehicleBrand.isNotEmpty() && (currentSettings.vehicleEnergy == "gas" || currentSettings.vehicleEnergy == "hybrid")) {
                    settingsManager.setUseVehicleFilter(true)
                } else {
                    settingsManager.setPoiProviderTypes(setOf(PoiProviderType.Routex))
                    settingsManager.setUseVehicleFilter(false)
                }
            } else if (path == "/electric_stations") {
                if (currentSettings.useVehicleFilter && (currentSettings.vehicleEnergy == "electric" || currentSettings.vehicleEnergy == "hybrid")) {
                    // Already configured
                } else if (currentSettings.vehicleBrand.isNotEmpty() && (currentSettings.vehicleEnergy == "electric" || currentSettings.vehicleEnergy == "hybrid")) {
                    settingsManager.setUseVehicleFilter(true)
                } else {
                    settingsManager.setPoiProviderTypes(setOf(PoiProviderType.DataGouvElec))
                    settingsManager.setUseVehicleFilter(false)
                }
            } else if (path == "/libremap") {
                settingsManager.setPhoneMapEngine(MapEngine.MapLibre)
            }
            showMap = true
        }
    }
    val settings by settingsManager.settings.collectAsState()

    LaunchedEffect(showMap, showRoutePlanning, showFavorites, isPlaystoreDistribution) {
        if (showMap || showRoutePlanning || showFavorites || isPlaystoreDistribution) onRequestMapDeps()
    }
    val paletteIndex by AnimationPalettes.index.collectAsState()
    val palette = remember(paletteIndex) { AnimationPalettes.paletteFor(paletteIndex) }
    val fallbackUpdateFlow = remember { MutableStateFlow<AppUpdateInfo?>(null) }
    val updateAvailable by (inAppUpdateHelper?.updateAvailable ?: fallbackUpdateFlow).collectAsState(initial = null)

    if (updateAvailable != null) {
        UpdateAvailableDialog(
            onCancel = { inAppUpdateHelper?.dismissUpdate() },
            onUpdate = { updateAvailable?.let { onStartUpdate(it) } }
        )
    }

    PlaystoreTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when {
                showNetworkDiagnostics -> {
                    BackHandler { showNetworkDiagnostics = false }
                    PhoneNetworkLocationScreen(
                        networkService = networkService,
                        onBack = { showNetworkDiagnostics = false }
                    )
                }
                isPlaystoreDistribution && showPlaystoreSettings -> {
                    BackHandler { showPlaystoreSettings = false }
                    SettingsScreen(
                        settingsManager = settingsManager,
                        authManager = authManager,
                        errorLog = errorLog,
                        onDismiss = { showPlaystoreSettings = false },
                        initialScreenStack = playstoreSettingsInitialStack,
                        onInitialRouteConsumed = { playstoreSettingsInitialStack = null }
                    )
                }
                isPlaystoreDistribution && showFuelForecast -> {
                    BackHandler { showFuelForecast = false }
                    FuelForecastScreen(
                        repository = fuelForecastRepository!!,
                        onBack = { showFuelForecast = false }
                    )
                }
                isPlaystoreDistribution && showDirectionsMap && mapDeps != null -> {
                    BackHandler { showDirectionsMap = false }
                    DirectionsMapScreen(
                        route = routeForDirections,
                        pois = stationsForDirections,
                        settingsManager = settingsManager,
                        onBack = { showDirectionsMap = false }
                    )
                }
                isPlaystoreDistribution && showFavorites && mapDeps != null -> {
                    FavoritesScreen(
                        favoritesRepo = mapDeps!!.favoritesRepo,
                        settingsManager = settingsManager,
                        onBack = { showFavorites = false },
                        onSelectPoi = { poi ->
                            pendingMapPoi = poi
                            showMap = true
                            showFavorites = false
                        },
                        onSelectLocation = { loc ->
                            initialNavDestination = NavDestination(address = loc.label, latitude = loc.latitude, longitude = loc.longitude)
                            showRoutePlanning = true
                            showMap = true
                            showFavorites = false
                        }
                    )
                }
                isPlaystoreDistribution && showMap && showRoutePlanning && mapDeps != null -> {
                    RoutePlanningScreen(
                        routePlanner = mapDeps!!.routePlanner,
                        routingClient = mapDeps!!.routingClient,
                        tollCalculator = mapDeps!!.tollCalculator,
                        trafficProviderFactory = mapDeps!!.trafficProviderFactory,
                        poiProvider = mapDeps!!.poiProvider,
                        geocodingClient = mapDeps!!.geocodingClient,
                        settingsManager = settingsManager,
                        onBack = { showRoutePlanning = false; initialNavDestination = null },
                        onShowOnMap = { route, pois ->
                            routeForDirections = route
                            stationsForDirections = pois
                            showDirectionsMap = true
                        },
                        initialDestination = initialNavDestination
                    )
                }
                isPlaystoreDistribution && showMap && mapDeps == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                isPlaystoreDistribution && showMap && mapDeps != null -> {
                    BackHandler {
                        showMap = false
                        pendingMapPoi = null
                    }
                    if (settings.phoneMapEngine == MapEngine.MapLibre) {
                        VectorMapScreen(
                            poiProvider = mapDeps!!.poiProvider,
                            availabilityProviderFactory = mapDeps!!.availabilityProviderFactory,
                            trafficProviderFactory = mapDeps!!.trafficProviderFactory,
                            settingsManager = settingsManager,
                            authManager = authManager,
                            diagnostics = diagnostics,
                            palette = palette,
                            onBack = {
                                showMap = false
                                pendingMapPoi = null
                            pendingMapLocation = null
                            },
                            onPlanRoute = { showRoutePlanning = true },
                            communityRepo = mapDeps!!.communityRepo,
                            favoritesRepo = mapDeps!!.favoritesRepo,
                        initialSelectedPoi = pendingMapPoi,
                        initialCenter = pendingMapLocation?.let { org.maplibre.android.geometry.LatLng(it.latitude, it.longitude) }
                        )
                    } else {
                        MapScreen(
                            poiProvider = mapDeps!!.poiProvider,
                            availabilityProviderFactory = mapDeps!!.availabilityProviderFactory,
                            trafficProviderFactory = mapDeps!!.trafficProviderFactory,
                            settingsManager = settingsManager,
                            authManager = authManager,
                            diagnostics = diagnostics,
                            palette = palette,
                            onBack = {
                                showMap = false
                                pendingMapPoi = null
                            pendingMapLocation = null
                            },
                            onPlanRoute = { showRoutePlanning = true },
                            communityRepo = mapDeps!!.communityRepo,
                            favoritesRepo = mapDeps!!.favoritesRepo,
                        initialSelectedPoi = pendingMapPoi,
                        initialCenter = pendingMapLocation
                        )
                    }
                }
                isPlaystoreDistribution && !showMap -> {
                    PhoneDashboardScreen(
                        settingsManager = settingsManager,
                        poiProvider = mapDeps?.poiProvider,
                        favoritesRepo = mapDeps?.favoritesRepo,
                        hasLocationPermission = hasLocationPermission,
                        mapDepsReady = mapDeps != null,
                        fuelForecastRepository = fuelForecastRepository,
                        geocodingClient = mapDeps?.geocodingClient,
                        isUpdateInProgress = isUpdateInProgress,
                        showAds = true,
                        onOpenMap = { poi ->
                            pendingMapPoi = poi
                            showMap = true
                        },
                        onOpenRoutes = { destination ->
                            initialNavDestination = destination
                            showRoutePlanning = true
                            showMap = true
                        },
                        onOpenFavorites = {
                            showFavorites = true
                        },
                        onOpenNetworkDiagnostics = { showNetworkDiagnostics = true },
                        onOpenFuelForecast = { showFuelForecast = true },
                        onOpenSettings = { stack ->
                            playstoreSettingsInitialStack = stack
                            showPlaystoreSettings = true
                        },
                        onRequestLocationPermission = onRequestLocationPermission,
                        selectedSearchLocation = dashboardSelectedLocation,
                        onLocationSelected = { dashboardSelectedLocation = it }
                    )
                }
                showSettings && !isPlaystoreDistribution -> {
                    SettingsScreen(
                        settingsManager = settingsManager,
                        authManager = authManager,
                        errorLog = errorLog,
                        onDismiss = { showSettings = false },
                        initialScreenStack = settingsInitialStack,
                        onInitialRouteConsumed = { settingsInitialStack = null }
                    )
                }
                showDirectionsMap && mapDeps != null -> {
                    BackHandler { showDirectionsMap = false }
                    DirectionsMapScreen(
                        route = routeForDirections,
                        pois = stationsForDirections,
                        settingsManager = settingsManager,
                        onBack = { showDirectionsMap = false }
                    )
                }
                showFavorites && mapDeps != null -> {
                    FavoritesScreen(
                        favoritesRepo = mapDeps!!.favoritesRepo,
                        settingsManager = settingsManager,
                        onBack = { showFavorites = false },
                        onSelectPoi = { poi ->
                            pendingMapPoi = poi
                            showMap = true
                            showFavorites = false
                        },
                        onSelectLocation = { loc ->
                            initialNavDestination = NavDestination(address = loc.label, latitude = loc.latitude, longitude = loc.longitude)
                            showRoutePlanning = true
                            showMap = true
                            showFavorites = false
                        }
                    )
                }
                showMap && showRoutePlanning && mapDeps != null -> {
                    RoutePlanningScreen(
                        routePlanner = mapDeps!!.routePlanner,
                        routingClient = mapDeps!!.routingClient,
                        tollCalculator = mapDeps!!.tollCalculator,
                        trafficProviderFactory = mapDeps!!.trafficProviderFactory,
                        poiProvider = mapDeps!!.poiProvider,
                        geocodingClient = mapDeps!!.geocodingClient,
                        settingsManager = settingsManager,
                        onBack = { showRoutePlanning = false; initialNavDestination = null },
                        onShowOnMap = { route, pois ->
                            routeForDirections = route
                            stationsForDirections = pois
                            showDirectionsMap = true
                        },
                        onSearchAtLocation = { lat, lon ->
                            pendingMapLocation = com.google.android.gms.maps.model.LatLng(lat, lon)
                            showRoutePlanning = false
                            showMap = true
                        },
                        initialDestination = initialNavDestination
                    )
                }
                showMap -> {
                    BackHandler {
                        showMap = false
                        pendingMapPoi = null
                        pendingMapLocation = null
                    }
                    if (mapDeps != null) {
                        if (settings.phoneMapEngine == MapEngine.MapLibre) {
                            VectorMapScreen(
                                poiProvider = mapDeps!!.poiProvider,
                                availabilityProviderFactory = mapDeps!!.availabilityProviderFactory,
                                trafficProviderFactory = mapDeps!!.trafficProviderFactory,
                                settingsManager = settingsManager,
                                authManager = authManager,
                                diagnostics = diagnostics,
                                palette = palette,
                                onBack = {
                                    showMap = false
                                    pendingMapPoi = null
                                    pendingMapLocation = null
                                },
                                onPlanRoute = { showRoutePlanning = true },
                                communityRepo = mapDeps!!.communityRepo,
                                favoritesRepo = mapDeps!!.favoritesRepo,
                                initialSelectedPoi = pendingMapPoi,
                                initialCenter = pendingMapLocation?.let { org.maplibre.android.geometry.LatLng(it.latitude, it.longitude) }
                            )
                        } else {
                            MapScreen(
                                poiProvider = mapDeps!!.poiProvider,
                                availabilityProviderFactory = mapDeps!!.availabilityProviderFactory,
                                trafficProviderFactory = mapDeps!!.trafficProviderFactory,
                                settingsManager = settingsManager,
                                authManager = authManager,
                                diagnostics = diagnostics,
                                palette = palette,
                                onBack = {
                                    showMap = false
                                    pendingMapPoi = null
                                    pendingMapLocation = null
                                },
                                onPlanRoute = { showRoutePlanning = true },
                                communityRepo = mapDeps!!.communityRepo,
                                favoritesRepo = mapDeps!!.favoritesRepo,
                                initialSelectedPoi = pendingMapPoi,
                                initialCenter = pendingMapLocation
                            )
                        }
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                !isPlaystoreDistribution && showFuelForecast && fuelForecastRepository != null -> {
                    BackHandler { showFuelForecast = false }
                    FuelForecastScreen(
                        repository = fuelForecastRepository,
                        onBack = { showFuelForecast = false }
                    )
                }
                else -> {
                    PlaystoreTheme {
                        PhoneDashboardScreen(
                            settingsManager = settingsManager,
                            poiProvider = mapDeps?.poiProvider,
                            favoritesRepo = mapDeps?.favoritesRepo,
                            hasLocationPermission = hasLocationPermission,
                            mapDepsReady = mapDeps != null,
                            fuelForecastRepository = fuelForecastRepository,
                            geocodingClient = mapDeps?.geocodingClient,
                            isUpdateInProgress = isUpdateInProgress,
                            showAds = isPlaystoreDistribution,
                            onOpenMap = { poi ->
                                pendingMapPoi = poi
                                showMap = true
                            },
                            onOpenRoutes = { destination ->
                                initialNavDestination = destination
                                showRoutePlanning = true
                                showMap = true
                            },
                            onOpenFavorites = {
                                showFavorites = true
                            },
                            onOpenNetworkDiagnostics = { showNetworkDiagnostics = true },
                            onOpenFuelForecast = { showFuelForecast = true },
                            onOpenSettings = { stack ->
                                settingsInitialStack = stack
                                showSettings = true
                            },
                            onRequestLocationPermission = onRequestLocationPermission,
                            selectedSearchLocation = dashboardSelectedLocation,
                            onLocationSelected = { dashboardSelectedLocation = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StartupErrorContent(error: Throwable) {
    val message = error.message ?: error.toString()
    val fullDetail = buildStartupErrorDetail(error)
    PlaystoreTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().padding(24.dp)) {
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Startup error",
                        color = Color(0xFFF87171),
                        fontSize = 20.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        message,
                        color = Color(0xFFE2E8F0),
                        fontSize = 14.sp
                    )
                    if (fullDetail.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            fullDetail,
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

private fun buildStartupErrorDetail(error: Throwable): String {
    val sb = StringBuilder()
    var t: Throwable? = error
    var depth = 0
    while (t != null && depth < 10) {
        if (depth > 0) sb.append("\n\nCaused by: ")
        sb.append(t.javaClass.name).append(": ").append(t.message ?: "(no message)")
        val stack = t.stackTrace
        val limit = (stack.size).coerceAtMost(20)
        for (i in 0 until limit) {
            sb.append("\n    at ").append(stack[i].toString())
        }
        if (stack.size > limit) sb.append("\n    ... ${stack.size - limit} more")
        t = t.cause
        depth++
    }
    return sb.toString()
}

