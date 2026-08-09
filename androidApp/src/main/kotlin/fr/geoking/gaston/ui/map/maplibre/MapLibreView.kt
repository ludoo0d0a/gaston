package fr.geoking.gaston.ui.map.maplibre

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

@Composable
fun MapLibreView(
    modifier: Modifier = Modifier,
    styleUrl: String = "https://tiles.openfreemap.org/styles/dark",
    styleJson: String? = null,
    cameraPosition: CameraPosition? = null,
    onMapReady: (MapLibreMap) -> Unit = {},
    onMapClick: (LatLng) -> Unit = {},
    update: (MapLibreMap) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var styleEpoch by remember { mutableIntStateOf(0) }

    // Initialize MapLibre singleton
    remember {
        MapLibre.getInstance(context)
    }

    val initialStyleUrl = styleUrl
    val initialStyleJson = styleJson

    val mapView = remember {
        MapView(context).apply {
            getMapAsync { map ->
                applyMapLibreStyle(map, initialStyleUrl, initialStyleJson) {
                    styleEpoch++
                }
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
            // Read epoch so AndroidView re-runs update after each style load.
            mapView.tag = styleEpoch
            mapView.getMapAsync { map ->
                update(map)
            }
        }
    )

    // Update style if it changes
    LaunchedEffect(styleUrl, styleJson) {
        mapView.getMapAsync { map ->
            applyMapLibreStyle(map, styleUrl, styleJson) {
                styleEpoch++
            }
        }
    }
}

private fun applyMapLibreStyle(
    map: MapLibreMap,
    styleUrl: String,
    styleJson: String?,
    onStyleLoaded: Style.OnStyleLoaded? = null,
) {
    if (styleJson != null) {
        map.setStyle(Style.Builder().fromJson(styleJson), onStyleLoaded)
    } else {
        map.setStyle(styleUrl, onStyleLoaded)
    }
}
