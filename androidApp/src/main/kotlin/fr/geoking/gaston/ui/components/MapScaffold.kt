package fr.geoking.gaston.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.effectiveProviders
import fr.geoking.gaston.effectiveProvidersAt
import fr.geoking.gaston.ui.anim.AnimationPalette

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
    content: @Composable (PaddingValues) -> Unit
) {
    val settings by settingsManager.settings.collectAsState()
    val filterBarProviders = remember(settings, mapCenterLatitude, mapCenterLongitude) {
        when {
            mapCenterLatitude != null && mapCenterLongitude != null ->
                settings.effectiveProvidersAt(mapCenterLatitude!!, mapCenterLongitude!!)
            else -> settings.effectiveProviders()
        }
    }
    var navMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(title, color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        if (onPlanRoute != null || onLocatePlace != null || onRouteToDirection != null) {
                            Box {
                                IconButton(onClick = { navMenuExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Directions,
                                        contentDescription = "Navigation",
                                        tint = Color.White
                                    )
                                }
                                DropdownMenu(
                                    expanded = navMenuExpanded,
                                    onDismissRequest = { navMenuExpanded = false }
                                ) {
                                    if (onPlanRoute != null) {
                                        DropdownMenuItem(
                                            text = { Text("Plan route") },
                                            leadingIcon = { Icon(Icons.Default.Directions, contentDescription = null) },
                                            onClick = {
                                                navMenuExpanded = false
                                                onPlanRoute()
                                            }
                                        )
                                    }
                                    if (onLocatePlace != null) {
                                        DropdownMenuItem(
                                            text = { Text("Locate a place") },
                                            leadingIcon = { Icon(Icons.Default.Place, contentDescription = null) },
                                            onClick = {
                                                navMenuExpanded = false
                                                onLocatePlace()
                                            }
                                        )
                                    }
                                    if (onRouteToDirection != null) {
                                        DropdownMenuItem(
                                            text = { Text("Route to a direction") },
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
                                contentDescription = "Refresh map",
                                tint = Color.White
                            )
                        }

                        IconButton(onClick = onShowSources) {
                            Icon(
                                imageVector = Icons.Default.Hub,
                                contentDescription = "Data sources",
                                tint = Color.White
                            )
                        }

                        IconButton(onClick = onShowSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Map settings",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0F172A)
                    )
                )

                // Unified Filter Bar
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A))
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    energySelectorItems(
                        settings = settings,
                        settingsManager = settingsManager,
                        providers = filterBarProviders
                    )
                }
            }
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (onShowFavoritesOnlyChange != null) {
                    FilterFab(
                        settingsManager = settingsManager,
                        mapCenterLatitude = mapCenterLatitude,
                        mapCenterLongitude = mapCenterLongitude,
                        favoritesFilterEnabled = favoritesFilterEnabled,
                        showFavoritesOnly = showFavoritesOnly,
                        onShowFavoritesOnlyChange = onShowFavoritesOnlyChange
                    )
                }
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
