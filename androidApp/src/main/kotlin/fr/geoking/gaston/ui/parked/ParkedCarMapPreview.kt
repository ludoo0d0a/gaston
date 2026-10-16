package fr.geoking.gaston.ui.parked

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import fr.geoking.gaston.R
import fr.geoking.gaston.ui.map.InspectableGoogleMap
import kotlin.math.abs

/**
 * Compact Google Map showing the retained / parked vehicle pin with a car icon and user's current location with a blue dot.
 * Pan and pinch-zoom are enabled; [onMapInteractionChanged] lets the parent
 * disable vertical scrolling while the user manipulates the map.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ParkedCarMapPreview(
    carLatitude: Double,
    carLongitude: Double,
    userLatitude: Double? = null,
    userLongitude: Double? = null,
    modifier: Modifier = Modifier,
    onMapInteractionChanged: (interacting: Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density

    val carMarkerIcon = remember(context, density) {
        BitmapDescriptorFactory.fromBitmap(createCarMarkerBitmap(context, density))
    }
    val blueDotIcon = remember(density) {
        BitmapDescriptorFactory.fromBitmap(createBlueDotBitmap(density))
    }

    val carTarget = LatLng(carLatitude, carLongitude)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(carTarget, MAP_ZOOM)
    }

    LaunchedEffect(carLatitude, carLongitude, userLatitude, userLongitude) {
        if (userLatitude != null && userLongitude != null &&
            (abs(carLatitude - userLatitude) > 1e-6 || abs(carLongitude - userLongitude) > 1e-6)
        ) {
            val userTarget = LatLng(userLatitude, userLongitude)
            val bounds = LatLngBounds.builder()
                .include(carTarget)
                .include(userTarget)
                .build()
            cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 100))
        } else {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(carTarget, MAP_ZOOM))
        }
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
        InspectableGoogleMap(
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
            Marker(
                state = rememberUpdatedMarkerState(position = carTarget),
                icon = carMarkerIcon,
                title = stringResource(R.string.parked_car_title),
            )
            if (userLatitude != null && userLongitude != null) {
                Marker(
                    state = rememberUpdatedMarkerState(position = LatLng(userLatitude, userLongitude)),
                    icon = blueDotIcon,
                    title = stringResource(R.string.emergency_your_location),
                    anchor = Offset(0.5f, 0.5f),
                )
            }
        }
    }
}

private fun createCarMarkerBitmap(context: Context, density: Float): Bitmap {
    val sizePx = (44 * density).toInt().coerceAtLeast(36)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E7D32")
        style = Paint.Style.FILL
    }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = (2.5f * density).coerceAtLeast(2f)
    }

    val radius = sizePx / 2f - strokePaint.strokeWidth
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, radius, bgPaint)
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, radius, strokePaint)

    val drawable = ContextCompat.getDrawable(context, R.drawable.ic_directions_car)
    if (drawable != null) {
        val iconSize = (radius * 1.2f).toInt()
        val left = (sizePx / 2f - iconSize / 2f).toInt()
        val top = (sizePx / 2f - iconSize / 2f).toInt()
        drawable.setBounds(left, top, left + iconSize, top + iconSize)
        drawable.setTint(Color.WHITE)
        drawable.draw(canvas)
    }

    return bitmap
}

private fun createBlueDotBitmap(density: Float): Bitmap {
    val sizePx = (32 * density).toInt().coerceAtLeast(24)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val cx = sizePx / 2f
    val cy = sizePx / 2f

    val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4285F4")
        alpha = 60
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, cx * 0.95f, auraPaint)

    val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, cx * 0.55f, whitePaint)

    val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A73E8")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, cx * 0.42f, bluePaint)

    return bitmap
}

private const val MAP_ZOOM = 16f
