package fr.geoking.gaston.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState

/**
 * Google Map host that is safe under Android Studio Preview / JVM screenshot tools
 * (Paparazzi, Robolectric Compose). When [LocalInspectionMode] is true, renders
 * [StaticGoogleMapImage] instead of the Maps SDK view.
 *
 * Force inspection in tests:
 * ```
 * CompositionLocalProvider(LocalInspectionMode provides true) { … }
 * ```
 */
@Composable
fun InspectableGoogleMap(
    modifier: Modifier = Modifier,
    cameraPositionState: CameraPositionState = rememberCameraPositionState(),
    properties: MapProperties = MapProperties(),
    uiSettings: MapUiSettings = MapUiSettings(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onMapClick: (LatLng) -> Unit = {},
    /** Optional pre-decoded Static Maps bitmap (screenshot tests). */
    staticMapBitmap: android.graphics.Bitmap? = null,
    content: @Composable @GoogleMapComposable () -> Unit = {},
) {
    if (LocalInspectionMode.current) {
        StaticGoogleMapImage(
            center = cameraPositionState.position.target,
            zoom = cameraPositionState.position.zoom,
            modifier = modifier,
            preloadedBitmap = staticMapBitmap,
        )
    } else {
        GoogleMap(
            modifier = modifier,
            cameraPositionState = cameraPositionState,
            properties = properties,
            uiSettings = uiSettings,
            contentPadding = contentPadding,
            onMapClick = onMapClick,
            content = content,
        )
    }
}

/** Default Preview / screenshot substitute for [InspectableGoogleMap]. */
@Composable
fun MapPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFE0F2FE),
                        Color(0xFFBAE6FD),
                        Color(0xFF7DD3FC),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text("Google Map", color = Color(0xFF0C4A6E))
    }
}
