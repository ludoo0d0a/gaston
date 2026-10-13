package fr.geoking.gaston.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.google.android.gms.maps.model.LatLng
import fr.geoking.gaston.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Bundled Paris roadmap used when network / Static API is unavailable. */
const val MARKETING_STATIC_MAP_ASSET = "marketing/map-paris-static.png"

/**
 * Builds a [Maps Static API](https://developers.google.com/maps/documentation/maps-static)
 * URL. Caller must enable **Maps Static API** on the same Google Cloud key as Maps SDK.
 */
fun buildStaticGoogleMapUrl(
    center: LatLng,
    zoom: Float,
    widthPx: Int,
    heightPx: Int,
    apiKey: String,
    scale: Int = 2,
    mapType: String = "roadmap",
): String {
    val w = widthPx.coerceIn(1, 640)
    val h = heightPx.coerceIn(1, 640)
    val s = scale.coerceIn(1, 2)
    return buildString {
        append("https://maps.googleapis.com/maps/api/staticmap")
        append("?center=")
        append(String.format(Locale.US, "%.6f,%.6f", center.latitude, center.longitude))
        append("&zoom=").append(zoom.toInt().coerceIn(0, 21))
        append("&size=").append(w).append('x').append(h)
        append("&scale=").append(s)
        append("&maptype=").append(mapType)
        // Hide default POI icons so Gaston markers stay readable on top.
        append("&style=feature:poi%7Cvisibility:off")
        append("&key=").append(apiKey)
    }
}

/**
 * Synchronous load for screenshot / JVM tests: network first when [apiKey] is set,
 * else (or on failure) the bundled [fallbackAsset] under `assets/`.
 */
fun loadStaticGoogleMapBitmap(
    context: Context,
    center: LatLng,
    zoom: Float,
    /** Logical CSS pixels (max 640 per side for Static Maps). */
    widthPx: Int = 360,
    heightPx: Int = 640,
    apiKey: String = BuildConfig.GOOGLE_MAPS_KEY,
    fallbackAsset: String = MARKETING_STATIC_MAP_ASSET,
    allowNetwork: Boolean = true,
): Bitmap? {
    if (allowNetwork && apiKey.isNotBlank()) {
        val url = buildStaticGoogleMapUrl(center, zoom, widthPx, heightPx, apiKey)
        runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 12_000
                requestMethod = "GET"
            }
            connection.inputStream.use { stream ->
                BitmapFactory.decodeStream(stream)?.let { return it }
            }
        }
    }
    return runCatching {
        context.assets.open(fallbackAsset).use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}

/**
 * Compose image of a static Google Map (Coil). Falls back to [MapPlaceholder] when
 * [preloadedBitmap] is null and [apiKey] is blank.
 */
@Composable
fun StaticGoogleMapImage(
    center: LatLng,
    zoom: Float,
    modifier: Modifier = Modifier,
    preloadedBitmap: Bitmap? = null,
    apiKey: String = BuildConfig.GOOGLE_MAPS_KEY,
    contentDescription: String? = "Google Map",
) {
    val context = LocalContext.current
    when {
        preloadedBitmap != null -> {
            Image(
                bitmap = remember(preloadedBitmap) { preloadedBitmap.asImageBitmap() },
                contentDescription = contentDescription,
                modifier = modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        apiKey.isNotBlank() -> {
            val url = remember(center, zoom, apiKey) {
                buildStaticGoogleMapUrl(center, zoom, 360, 640, apiKey)
            }
            Box(modifier.fillMaxSize()) {
                MapPlaceholder(Modifier.fillMaxSize())
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(url)
                        .crossfade(false)
                        .build(),
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        else -> {
            val assetBitmap = remember(context) {
                runCatching {
                    context.assets.open(MARKETING_STATIC_MAP_ASSET).use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
            }
            if (assetBitmap != null) {
                Image(
                    bitmap = remember(assetBitmap) { assetBitmap.asImageBitmap() },
                    contentDescription = contentDescription,
                    modifier = modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                MapPlaceholder(modifier)
            }
        }
    }
}
