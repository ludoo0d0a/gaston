package fr.geoking.gaston.ui.components

import fr.geoking.gaston.R
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import fr.geoking.gaston.BuildConfig
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.ThemeMode
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
    /** Map center for auto mode provider resolution and country label; null uses settings fallback. */
    mapCenterLatitude: Double? = null,
    mapCenterLongitude: Double? = null,
    showAds: Boolean = false,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val settings by settingsManager.settings.collectAsState()
    var navMenuExpanded by remember { mutableStateOf(false) }
    var moreMenuExpanded by remember { mutableStateOf(false) }

    val toggleMapTheme = {
        val nextMode = when (settings.mapThemeMode) {
            ThemeMode.System -> ThemeMode.Light
            ThemeMode.Light -> ThemeMode.Dark
            ThemeMode.Dark -> ThemeMode.System
        }
        settingsManager.saveSettings(settings.copy(mapThemeMode = nextMode))
    }

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
                    if (onLocatePlace != null) {
                        IconButton(onClick = onLocatePlace) {
                            Icon(
                                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_waypoint),
                                contentDescription = stringResource(R.string.route_locate_place)
                            )
                        }
                    }

                    IconButton(onClick = onLocateMe) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = stringResource(R.string.action_locate_me)
                        )
                    }

                    if (onRouteToDirection != null) {
                        IconButton(onClick = onRouteToDirection) {
                            Icon(
                                imageVector = Icons.Default.Directions,
                                contentDescription = stringResource(R.string.route_to_direction)
                            )
                        }
                    }

                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.cd_refresh_map)
                        )
                    }

                    Box {
                        IconButton(onClick = { moreMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.screen_more)
                            )
                        }
                        DropdownMenu(
                            expanded = moreMenuExpanded,
                            onDismissRequest = { moreMenuExpanded = false }
                        ) {
                            if (onPlanRoute != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.route_plan_menu)) },
                                    leadingIcon = { Icon(Icons.Default.Directions, contentDescription = null) },
                                    onClick = {
                                        moreMenuExpanded = false
                                        onPlanRoute()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.screen_theme)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = when (settings.mapThemeMode) {
                                            ThemeMode.System -> Icons.Default.BrightnessAuto
                                            ThemeMode.Light -> Icons.Default.LightMode
                                            ThemeMode.Dark -> Icons.Default.DarkMode
                                        },
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    moreMenuExpanded = false
                                    toggleMapTheme()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.screen_sources)) },
                                leadingIcon = { Icon(Icons.Default.Hub, contentDescription = null) },
                                onClick = {
                                    moreMenuExpanded = false
                                    onShowSources()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.screen_map_settings)) },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = {
                                    moreMenuExpanded = false
                                    onShowSettings()
                                }
                            )
                        }
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
        content(padding)
    }
}
