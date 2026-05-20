package fr.geoking.gaston.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import fr.geoking.gaston.R
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import fr.geoking.gaston.AppSettings
import fr.geoking.gaston.api.geocoding.GeocodedPlace
import fr.geoking.gaston.api.geocoding.GeocodingClient
import fr.geoking.gaston.feature.location.LocationHelper
import fr.geoking.gaston.intent.NavDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Minimum query length before opening remote address/city autocomplete on the phone dashboard. */
private const val PHONE_DEST_AUTOCOMPLETE_MIN_CHARS = 3

@Composable
fun PhoneDashboardDestinationSearch(
    geocodingClient: GeocodingClient?,
    hasLocationPermission: Boolean,
    userLat: Double?,
    userLon: Double?,
    selectedSearchLocation: GeocodedPlace?,
    settings: AppSettings,
    onLocationSelected: (GeocodedPlace?) -> Unit,
    onOpenRoutes: (NavDestination?, NavDestination?) -> Unit
) {
    val context = LocalContext.current
    var destQuery by remember(selectedSearchLocation?.label) {
        mutableStateOf(selectedSearchLocation?.label.orEmpty())
    }
    var destSuggestions by remember { mutableStateOf<List<GeocodedPlace>>(emptyList()) }
    var destFocused by remember { mutableStateOf(false) }
    var destFieldHeight by remember { mutableIntStateOf(0) }

    LaunchedEffect(
        destQuery,
        settings.favoriteLocations,
        settings.routeHistory,
        selectedSearchLocation,
        hasLocationPermission,
        userLat,
        userLon,
        geocodingClient
    ) {
        if (destQuery.isBlank() || destQuery == selectedSearchLocation?.label) {
            destSuggestions = emptyList()
            return@LaunchedEffect
        }
        if (destQuery.length < PHONE_DEST_AUTOCOMPLETE_MIN_CHARS) {
            destSuggestions = emptyList()
            return@LaunchedEffect
        }

        val historyMatches = settings.routeHistory.filter { it.label.contains(destQuery, ignoreCase = true) }
        val favoriteMatches = settings.favoriteLocations.filter { it.label.contains(destQuery, ignoreCase = true) }
        val localSuggestions = (favoriteMatches + historyMatches).distinctBy { it.label }
        destSuggestions = localSuggestions

        val client = geocodingClient ?: return@LaunchedEffect

        delay(300)
        try {
            val biasPair: Pair<Double, Double>? = when {
                !hasLocationPermission -> null
                userLat != null && userLon != null -> userLat to userLon
                else -> {
                    val loc = withContext(Dispatchers.IO) { LocationHelper.getCurrentLocation(context) }
                    if (loc != null) loc.latitude to loc.longitude else null
                }
            }
            val biasLat = biasPair?.first
            val biasLon = biasPair?.second
            val remote = client.geocode(
                destQuery,
                limit = 8,
                biasLatitude = biasLat,
                biasLongitude = biasLon
            )
            destSuggestions = (favoriteMatches + historyMatches + remote).distinctBy { it.label }
        } catch (_: Exception) {
            // Keep local suggestions only
        }
    }

    Box {
        OutlinedTextField(
            value = destQuery,
            onValueChange = { destQuery = it },
            placeholder = { Text(stringResource(R.string.route_where_to)) },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { destFocused = it.isFocused }
                .onSizeChanged { destFieldHeight = it.height },
            shape = RoundedCornerShape(24.dp),
            singleLine = true,
            leadingIcon = {
                IconButton(onClick = {
                    destQuery = ""
                    onLocationSelected(null)
                }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_gps_fixed),
                        contentDescription = stringResource(R.string.action_use_current_location),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (destQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            destQuery = ""
                            onLocationSelected(null)
                        }) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_clear))
                        }
                    }
                    IconButton(onClick = {
                        onOpenRoutes(
                            null,
                            selectedSearchLocation?.let {
                                NavDestination(
                                    address = it.label,
                                    latitude = it.latitude,
                                    longitude = it.longitude
                                )
                            }
                        )
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_directions),
                            contentDescription = stringResource(R.string.action_open_routes),
                            tint = MaterialTheme.colorScheme.primary
                        )
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
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                        items(destSuggestions) { suggestion ->
                            val isHistory = settings.routeHistory.any {
                                it.label == suggestion.label &&
                                    it.latitude == suggestion.latitude &&
                                    it.longitude == suggestion.longitude
                            }
                            val isFavorite = settings.favoriteLocations.any {
                                it.label == suggestion.label &&
                                    it.latitude == suggestion.latitude &&
                                    it.longitude == suggestion.longitude
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        destQuery = suggestion.label
                                        destFocused = false
                                        onLocationSelected(suggestion)
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(
                                        when {
                                            isFavorite -> R.drawable.ic_star
                                            isHistory -> R.drawable.ic_history
                                            else -> R.drawable.ic_place
                                        }
                                    ),
                                    contentDescription = null,
                                    tint = if (isFavorite) Color(0xFFFACC15) else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                        alpha = 0.6f
                                    ),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = suggestion.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
