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
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.ui.map.MarkerStyle
import fr.geoking.gaston.ui.map.PoiMarkerHelper

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

@Composable
internal fun MapMarkerPreviewCanvas(
    markerSpecs: List<PreviewMapMarkerSpec>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val pois = remember(markerSpecs) { markerSpecs.map { it.poi } }
    val bounds = remember(markerSpecs) { boundsForPois(pois) }
    /** Minimum inset from preview edge (frame). */
    val framePadDp = 2.dp

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
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
                        sizePx = PREVIEW_MARKER_SIZE_PX,
                        markerStyle = MarkerStyle.Circle
                    )
                }
                val imageBitmap = remember(poi.id, spec.effectiveEnergyTypes, spec.effectivePowerLevels) {
                    androidBitmap.asImageBitmap()
                }

                val framePadPx = with(density) { framePadDp.toPx() }
                val bw = androidBitmap.width.toFloat()
                val bh = androidBitmap.height.toFloat()
                val boxW = with(density) { maxWidth.toPx() }
                val boxH = with(density) { maxHeight.toPx() }
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
                val xPx = xBase - bw / 2f
                val yPx = yBase - bh
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
                        color = Color(0xFFE2E8F0),
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
