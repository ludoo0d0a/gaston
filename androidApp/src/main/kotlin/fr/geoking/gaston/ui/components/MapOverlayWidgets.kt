package fr.geoking.gaston.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.cos

@Composable
fun MapOverlayWidgets(
    bearing: Float,
    zoom: Float,
    latitude: Double,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        // 2. Scale widget (metric)
        MapScaleWidget(zoom = zoom, latitude = latitude)
    }
}

@Composable
fun MapCompassWidget(
    bearing: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(40.dp)) {
        val cx = size.width / 2
        val cy = size.height / 2
        val needleLength = size.height * 0.4f
        val needleWidth = size.width * 0.25f

        rotate(-bearing, pivot = Offset(cx, cy)) {
            // Draw a background circle
            drawCircle(
                color = Color.Black.copy(alpha = 0.3f),
                radius = size.width / 2,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.5f),
                radius = size.width / 2,
                center = Offset(cx, cy),
                style = Stroke(width = 1.dp.toPx())
            )

            // Red triangle (North pointer)
            val redPath = Path().apply {
                moveTo(cx, cy - needleLength)
                lineTo(cx - needleWidth / 2, cy)
                lineTo(cx + needleWidth / 2, cy)
                close()
            }
            drawPath(redPath, color = Color.Red)

            // White triangle (South pointer)
            val whitePath = Path().apply {
                moveTo(cx, cy + needleLength)
                lineTo(cx - needleWidth / 2, cy)
                lineTo(cx + needleWidth / 2, cy)
                close()
            }
            drawPath(whitePath, color = Color.White)

            // Draw outlines
            drawPath(redPath, color = Color.Black, style = Stroke(width = 1.dp.toPx()))
            drawPath(whitePath, color = Color.Black, style = Stroke(width = 1.dp.toPx()))

            // Center pivot point
            drawCircle(
                color = Color.DarkGray,
                radius = 3.dp.toPx(),
                center = Offset(cx, cy)
            )
            drawCircle(
                color = Color.White,
                radius = 1.5.dp.toPx(),
                center = Offset(cx, cy)
            )
        }
    }
}

@Composable
fun MapScaleWidget(
    zoom: Float,
    latitude: Double,
    modifier: Modifier = Modifier
) {
    val (scaleWidthDp, distanceText) = remember(zoom, latitude) {
        // Standard Mercator projection calculation (meters per coordinate pixel / DP)
        val metersPerPixel = 156543.03392 * cos(Math.toRadians(latitude)) / Math.pow(2.0, zoom.toDouble())

        // Target scale length on screen: about 80dp
        val targetMeters = 80.0 * metersPerPixel

        val distances = doubleArrayOf(
            1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0,
            1000.0, 2000.0, 5000.0, 10000.0, 20000.0, 50000.0, 100000.0, 200000.0, 500000.0
        )
        val selectedDistance = distances.minByOrNull { Math.abs(it - targetMeters) } ?: 100.0
        val widthDp = (selectedDistance / metersPerPixel).dp

        val text = if (selectedDistance >= 1000.0) {
            "${(selectedDistance / 1000.0).toInt()} km"
        } else {
            "${selectedDistance.toInt()} m"
        }

        Pair(widthDp, text)
    }

    Column(
        modifier = modifier.width(IntrinsicSize.Min),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = distanceText,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(modifier = Modifier.height(2.dp))
        Canvas(
            modifier = Modifier
                .width(scaleWidthDp)
                .height(6.dp)
        ) {
            val h = size.height
            val w = size.width
            val strokeWidthPx = 1.5.dp.toPx()

            // Horizontal line
            drawLine(
                color = Color.White,
                start = Offset(0f, h / 2),
                end = Offset(w, h / 2),
                strokeWidth = strokeWidthPx
            )
            // Left vertical tick
            drawLine(
                color = Color.White,
                start = Offset(0f, 0f),
                end = Offset(0f, h),
                strokeWidth = strokeWidthPx
            )
            // Right vertical tick
            drawLine(
                color = Color.White,
                start = Offset(w, 0f),
                end = Offset(w, h),
                strokeWidth = strokeWidthPx
            )
        }
    }
}
