package fr.geoking.gaston.ui.map.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.ui.map.MarkerStyle
import fr.geoking.gaston.ui.map.PoiMarkerHelper
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/** Same marker width as [fr.geoking.gaston.ui.MapScreen] / [PoiMarkerHelper]. */
private const val PREVIEW_MARKER_SIZE_PX = 96

private data class PreviewLatLngBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double,
)

private fun boundsForPois(pois: List<Poi>): PreviewLatLngBounds {
    if (pois.isEmpty()) {
        return PreviewLatLngBounds(48.865, 48.869, 2.350, 2.356)
    }
    val minLat = pois.minOf { it.latitude }
    val maxLat = pois.maxOf { it.latitude }
    val minLng = pois.minOf { it.longitude }
    val maxLng = pois.maxOf { it.longitude }
    val dLat = (maxLat - minLat).coerceAtLeast(0.0005)
    val dLng = (maxLng - minLng).coerceAtLeast(0.0005)
    val pLat = dLat * 0.12
    val pLng = dLng * 0.12
    return PreviewLatLngBounds(minLat - pLat, maxLat + pLat, minLng - pLng, maxLng + pLng)
}

/** Web Mercator world pixel (Google Maps tile scheme). */
private fun mercatorWorldPx(lat: Double, lng: Double, zoom: Float): Pair<Double, Double> {
    val scale = 256.0 * 2.0.pow(zoom.toDouble())
    val x = (lng + 180.0) / 360.0 * scale
    val sinLat = sin(Math.toRadians(lat)).coerceIn(-0.9999, 0.9999)
    val y = (0.5 - ln((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * PI)) * scale
    return x to y
}

/** Screen pixel for [lat]/[lng] given a Static Maps center/zoom and view size. */
private fun staticMapScreenXy(
    lat: Double,
    lng: Double,
    center: LatLng,
    zoom: Float,
    widthPx: Float,
    heightPx: Float,
): Pair<Float, Float> {
    val (cx, cy) = mercatorWorldPx(center.latitude, center.longitude, zoom)
    val (x, y) = mercatorWorldPx(lat, lng, zoom)
    return (x - cx + widthPx / 2.0).toFloat() to (y - cy + heightPx / 2.0).toFloat()
}

@Composable
internal fun MapMarkerPreviewCanvas(
    markerSpecs: List<PreviewMapMarkerSpec>,
    modifier: Modifier = Modifier,
    /** When false, only pins/legends are drawn (e.g. over a Static Maps image). */
    drawBackground: Boolean = true,
    /**
     * When set (with [viewportZoom]), place pins with Web Mercator so they align with a
     * Maps Static API image of the same center/zoom under [ContentScale.Crop].
     */
    viewportCenter: LatLng? = null,
    viewportZoom: Float? = null,
    /** Logical Static Maps `size=` (before `scale=`). */
    viewportMapWidthPx: Int = 360,
    viewportMapHeightPx: Int = 640,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val pois = remember(markerSpecs) { markerSpecs.map { it.poi } }
    val bounds = remember(markerSpecs) { boundsForPois(pois) }
    /** Minimum inset from preview edge (frame). */
    val framePadDp = 2.dp

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (drawBackground) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFE0F2FE),
                            Color(0xFFBAE6FD),
                            Color(0xFF7DD3FC)
                        )
                    )
                )
                val gridColor = Color(0x1A0C4A6E)
                val cols = 5
                val rows = 5
                val sx = size.width / cols
                val sy = size.height / rows
                for (i in 0..cols) {
                    drawLine(
                        color = gridColor,
                        start = Offset(i * sx, 0f),
                        end = Offset(i * sx, size.height),
                        strokeWidth = 1f
                    )
                }
                for (j in 0..rows) {
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, j * sy),
                        end = Offset(size.width, j * sy),
                        strokeWidth = 1f
                    )
                }
            }
        }

        val latRange = bounds.maxLat - bounds.minLat
        val lngRange = bounds.maxLng - bounds.minLng

        markerSpecs.forEach { spec ->
            val poi = spec.poi
            key(poi.id) {
                val androidBitmap = remember(poi.id, spec.effectiveEnergyTypes, spec.effectivePowerLevels) {
                    PoiMarkerHelper.getMarkerBitmap(
                        context = context,
                        poi = poi,
                        effectiveEnergyTypes = spec.effectiveEnergyTypes,
                        effectivePowerLevels = spec.effectivePowerLevels,
                        cheapestRank = null,
                        sizePx = PREVIEW_MARKER_SIZE_PX,
                        markerStyle = MarkerStyle.Circle
                    )
                }
                val imageBitmap = remember(poi.id, spec.effectiveEnergyTypes, spec.effectivePowerLevels) {
                    androidBitmap.asImageBitmap()
                }

                val bw = androidBitmap.width.toFloat()
                val bh = androidBitmap.height.toFloat()
                val boxW = with(density) { maxWidth.toPx() }
                val boxH = with(density) { maxHeight.toPx() }
                val (xPx, yPx) = if (viewportCenter != null && viewportZoom != null) {
                    val mapW = viewportMapWidthPx.toFloat()
                    val mapH = viewportMapHeightPx.toFloat()
                    val (sx, sy) = staticMapScreenXy(
                        poi.latitude, poi.longitude, viewportCenter, viewportZoom, mapW, mapH,
                    )
                    // Match Coil/Compose ContentScale.Crop of the static image into this box.
                    val scale = max(boxW / mapW, boxH / mapH)
                    val originX = (boxW - mapW * scale) / 2f
                    val originY = (boxH - mapH * scale) / 2f
                    (originX + sx * scale - bw / 2f) to (originY + sy * scale - bh)
                } else {
                    val framePadPx = with(density) { framePadDp.toPx() }
                    // Extra insets so marker pill (top), pin width, and legend under each pin don’t clip.
                    val insetHalfW = bw / 2f + with(density) { 2.dp.toPx() }
                    val insetTop = bh + with(density) { 2.dp.toPx() }
                    val insetBottom = with(density) { 34.dp.toPx() }
                    val innerW = (boxW - 2 * framePadPx - 2 * insetHalfW).coerceAtLeast(1f)
                    val innerH = (boxH - 2 * framePadPx - insetTop - insetBottom).coerceAtLeast(1f)
                    val xBase = framePadPx + insetHalfW +
                        ((poi.longitude - bounds.minLng) / lngRange).toFloat() * innerW
                    val yBase = framePadPx + insetTop +
                        (1.0 - (poi.latitude - bounds.minLat) / latRange).toFloat() * innerH
                    (xBase - bw / 2f) to (yBase - bh)
                }
                val xOffsetDp = (xPx / density.density).dp
                val yOffsetDp = (yPx / density.density).dp
                val wDp = (bw / density.density).dp
                val hDp = (bh / density.density).dp

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .offset(xOffsetDp, yOffsetDp)
                        .widthIn(max = 120.dp)
                ) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = poi.name,
                        modifier = Modifier
                            .width(wDp)
                            .height(hDp)
                    )
                    Text(
                        text = spec.legend,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (drawBackground) Color(0xFFE2E8F0) else Color(0xFF0F172A),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun MapPoiMarkersPreviewContent(
    markerSpecs: List<PreviewMapMarkerSpec>,
) {
    MaterialTheme {
        MapMarkerPreviewCanvas(
            markerSpecs = markerSpecs,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF0F172A,
    name = "Map canvas — 7 IRVE (1 band each + no filter) + 6 fuels (1 type each + none)",
    widthDp = 520,
    heightDp = 360
)
@Composable
private fun MapPoiMarkersPreviewDiverse() {
    MapPoiMarkersPreviewContent(
        markerSpecs = PreviewPoiSamples.diverseMapMarkerSpecs(),
    )
}
