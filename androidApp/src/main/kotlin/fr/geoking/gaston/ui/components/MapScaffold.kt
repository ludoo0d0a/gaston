package fr.geoking.gaston.ui.components

import fr.geoking.gaston.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import fr.geoking.gaston.BuildConfig
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.ui.anim.AnimationPalette
import fr.geoking.gaston.ui.components.AdMobBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScaffold(
    title: String,
    settingsManager: SettingsManager,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLocateMe: () -> Unit,
    onShowSettings: () -> Unit,
    onShowSources: () -> Unit,
    onPlanRoute: (() -> Unit)? = null,
    onLocatePlace: (() -> Unit)? = null,
    onRouteToDirection: (() -> Unit)? = null,
    showFavoritesOnly: Boolean = false,
    onShowFavoritesOnlyChange: ((Boolean) -> Unit)? = null,
    favoritesFilterEnabled: Boolean = false,
    isLoading: Boolean = false,
    palette: AnimationPalette? = null,
    /** Map center for auto mode provider resolution and country label; null uses settings fallback. */
    mapCenterLatitude: Double? = null,
    mapCenterLongitude: Double? = null,
    showAds: Boolean = false,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val settings by settingsManager.settings.collectAsState()
    var navMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = floatingActionButton,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    if (onPlanRoute != null || onLocatePlace != null || onRouteToDirection != null) {
                        Box {
                            IconButton(onClick = { navMenuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.Directions,
                                    contentDescription = stringResource(R.string.cd_navigation)
                                )
                            }
                            DropdownMenu(
                                expanded = navMenuExpanded,
                                onDismissRequest = { navMenuExpanded = false }
                            ) {
                                if (onPlanRoute != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.route_plan_menu)) },
                                        leadingIcon = { Icon(Icons.Default.Directions, contentDescription = null) },
                                        onClick = {
                                            navMenuExpanded = false
                                            onPlanRoute()
                                        }
                                    )
                                }
                                if (onLocatePlace != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.route_locate_place)) },
                                        leadingIcon = { Icon(Icons.Default.Place, contentDescription = null) },
                                        onClick = {
                                            navMenuExpanded = false
                                            onLocatePlace()
                                        }
                                    )
                                }
                                if (onRouteToDirection != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.route_to_direction)) },
                                        leadingIcon = { Icon(Icons.Default.Directions, contentDescription = null) },
                                        onClick = {
                                            navMenuExpanded = false
                                            onRouteToDirection()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.cd_refresh_map)
                        )
                    }

                    IconButton(onClick = onShowSources) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = stringResource(R.string.cd_data_sources)
                        )
                    }

                    IconButton(onClick = onShowSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.cd_map_settings)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
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
        Box(modifier = Modifier.fillMaxSize()) {
            content(padding)

            if (isLoading && palette != null) {
                MapLoader(
                    palette = palette,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(1f)
                )
            }
        }
    }
}
