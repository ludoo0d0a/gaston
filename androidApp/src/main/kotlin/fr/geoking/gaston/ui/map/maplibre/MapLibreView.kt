package fr.geoking.gaston.ui.map.maplibre

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

@Composable
fun MapLibreView(
    modifier: Modifier = Modifier,
    styleUrl: String = "https://tiles.openfreemap.org/styles/dark",
    cameraPosition: CameraPosition? = null,
    onMapReady: (MapLibreMap) -> Unit = {},
    onMapClick: (LatLng) -> Unit = {},
    update: (MapLibreMap) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Initialize MapLibre singleton
    remember {
        MapLibre.getInstance(context)
    }

    val mapView = remember {
        MapView(context).apply {
            getMapAsync { map ->
                map.setStyle(styleUrl)
                cameraPosition?.let { map.cameraPosition = it }
                map.addOnMapClickListener { point ->
                    onMapClick(point)
                    true
                }
                onMapReady(map)
            }
        }
    }

    // Handle lifecycle events
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = {
            mapView.getMapAsync { map ->
                update(map)
            }
        }
    )

    // Update style if it changes
    LaunchedEffect(styleUrl) {
        mapView.getMapAsync { map ->
            map.setStyle(styleUrl)
        }
    }
}
