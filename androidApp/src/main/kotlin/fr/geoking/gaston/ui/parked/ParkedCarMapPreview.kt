package fr.geoking.gaston.ui.parked

import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState

/**
 * Compact Google Map showing the retained / parked vehicle pin.
 * Pan and pinch-zoom are enabled; [onMapInteractionChanged] lets the parent
 * disable vertical scrolling while the user manipulates the map.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ParkedCarMapPreview(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
    onMapInteractionChanged: (interacting: Boolean) -> Unit = {},
) {
    val target = LatLng(latitude, longitude)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(target, MAP_ZOOM)
    }
    val markerState = rememberUpdatedMarkerState(position = target)
    LaunchedEffect(latitude, longitude) {
        cameraPositionState.position = CameraPosition.fromLatLngZoom(target, MAP_ZOOM)
    }
    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            onMapInteractionChanged(false)
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> onMapInteractionChanged(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (!cameraPositionState.isMoving) {
                            onMapInteractionChanged(false)
                        }
                    }
                }
                false
            }
            .testTag("parked_car_map"),
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(
                zoomControlsEnabled = true,
                zoomGesturesEnabled = true,
                scrollGesturesEnabled = true,
                myLocationButtonEnabled = false,
                mapToolbarEnabled = false,
                compassEnabled = false,
            ),
        ) {
            Marker(state = markerState)
        }
    }
}

private const val MAP_ZOOM = 16f
