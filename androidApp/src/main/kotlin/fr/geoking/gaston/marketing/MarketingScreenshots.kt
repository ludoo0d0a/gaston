@file:OptIn(ExperimentalMaterial3Api::class)

package fr.geoking.gaston.marketing

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.geoking.gaston.R
import fr.geoking.gaston.ThemeMode
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.ui.BrandHelper
import fr.geoking.gaston.ui.MAP_ENERGY_OPTIONS
import fr.geoking.gaston.ui.MAP_IRVE_POWER_OPTIONS
import fr.geoking.gaston.ui.components.FuelFilterChip
import fr.geoking.gaston.ui.components.PowerFilterChip
import fr.geoking.gaston.ui.dashboard.GastonTheme
import fr.geoking.gaston.ui.map.PoiDetailCard
import fr.geoking.gaston.ui.map.PoiDetailsFullscreenDialog
import fr.geoking.gaston.ui.map.PoiMarkerHelper
import fr.geoking.gaston.ui.map.preview.MapMarkerPreviewCanvas
import fr.geoking.gaston.ui.map.preview.PreviewPoiSamples

private val AutoListBackground = Color(0xFF0F0F10)
private val AutoListSurface = Color(0xFF1C1C1E)
private val AutoListText = Color(0xFFF2F2F7)
private val AutoListMuted = Color(0xFF8E8E93)

@Composable
fun MarketingMapScreen() {
    GastonTheme(themeMode = ThemeMode.Light) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    navigationIcon = {
                        IconButton(onClick = {}) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                        }
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.MyLocation, contentDescription = null)
                        }
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Settings, contentDescription = null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                MapMarkerPreviewCanvas(
                    markerSpecs = PreviewPoiSamples.marketingMapMarkerSpecs(),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                val cards = PreviewPoiSamples.marketingMapCarouselPois()
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(cards, key = { it.id }) { poi ->
                        val highlightFuels = if (poi.isElectric) emptySet() else setOf("gazole")
                        val highlightPower = if (poi.isElectric) setOf(200, 300) else emptySet()
                        PoiDetailCard(
                            poi = poi,
                            highlightedFuelIds = highlightFuels,
                            highlightedPowerLevels = highlightPower,
                            onNavigate = {},
                            onLocate = {},
                            onShowDetails = {},
                            isSelected = poi.id == cards.first().id,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MarketingFuelDetailScreen() {
    GastonTheme(themeMode = ThemeMode.Light) {
        PoiDetailsFullscreenDialog(
            poi = PreviewPoiSamples.marketingFuelDetailPoi(),
            highlightedFuelIds = setOf("gazole", "sp95"),
            onDismiss = {},
            embedded = true,
        )
    }
}

@Composable
fun MarketingEvDetailScreen() {
    GastonTheme(themeMode = ThemeMode.Light) {
        PoiDetailsFullscreenDialog(
            poi = PreviewPoiSamples.marketingEvDetailPoi(),
            highlightedPowerLevels = setOf(200, 300),
            onDismiss = {},
            embedded = true,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MarketingFiltersScreen() {
    GastonTheme(themeMode = ThemeMode.Light) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.filters)) },
                    navigationIcon = {
                        IconButton(onClick = {}) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.padding(end = 16.dp))
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    stringResource(R.string.screen_energy_types),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MAP_ENERGY_OPTIONS.filter { it.first != "electric" }.forEach { (id, label) ->
                        FuelFilterChip(
                            id = id,
                            label = label,
                            isSelected = id in setOf("gazole", "sp95"),
                            onClick = {},
                        )
                    }
                }
                Text(
                    stringResource(R.string.filter_section_power_range),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MAP_IRVE_POWER_OPTIONS.forEach { (kw, label) ->
                        PowerFilterChip(
                            kw = kw,
                            label = label,
                            isSelected = kw in setOf(50, 150),
                            onClick = {},
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MarketingAndroidAutoListScreen() {
    val pois = PreviewPoiSamples.marketingAutoListPois()
    val userLat = 48.867
    val userLon = 2.363

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AutoListBackground,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AutoListSurface)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.dashboard_nearest_stations),
                    color = AutoListText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    tint = AutoListMuted,
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(pois, key = { it.id }) { poi ->
                    val brandInfo = BrandHelper.getBrandInfo(poi.brand)
                    val iconRes = PoiMarkerHelper.headDrawableResId(poi, brandInfo)
                    val label = PoiMarkerHelper.getPoiLabel(poi, emptySet(), setOf(200, 300))
                    val title = poi.siteName?.takeIf { it.isNotBlank() } ?: poi.name
                    val meters = haversineMeters(userLat, userLon, poi.latitude, poi.longitude)
                    val distance = if (meters >= 1000) "%.1f km".format(meters / 1000) else "${meters.toInt()} m"
                    val subtitle = buildList {
                        add(distance)
                        if (!label.isNullOrBlank()) add(label)
                    }.joinToString(" · ")

                    ListItem(
                        headlineContent = {
                            Text(title, color = AutoListText, fontWeight = FontWeight.Medium)
                        },
                        supportingContent = {
                            Text(subtitle, color = AutoListMuted)
                        },
                        leadingContent = {
                            MarketingBrandIcon(iconRes = iconRes)
                        },
                        colors = ListItemDefaults.colors(containerColor = AutoListBackground),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MarketingBrandIcon(iconRes: Int, modifier: Modifier = Modifier.size(40.dp)) {
    val context = LocalContext.current
    val image = remember(iconRes) {
        val drawable = ContextCompat.getDrawable(context, iconRes) ?: return@remember null
        val sizePx = 96
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        bitmap.asImageBitmap()
    }
    if (image != null) {
        Image(bitmap = image, contentDescription = null, modifier = modifier)
    }
}

private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
        kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
        kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return r * c
}
