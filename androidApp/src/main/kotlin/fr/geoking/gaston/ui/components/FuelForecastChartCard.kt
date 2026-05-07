package fr.geoking.gaston.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.geoking.gaston.repository.DailyPricePoint
import fr.geoking.gaston.repository.FuelForecastUiState
import fr.geoking.gaston.ui.ColorHelper
import java.util.Locale
import kotlin.math.max

@Composable
fun FuelForecastCompactCard(
    state: FuelForecastUiState,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading && state.historyPoints.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalGasStation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    val price = state.historyPoints.lastOrNull()?.priceEurPerL
                    Text(
                        text = if (price != null) "€%.3f".format(price) else "—",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnifiedFuelForecastChartCard(
    state: FuelForecastUiState,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Price Comparison (normalized)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Relative trends of local fuels vs Brent. 14-day history.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isLoading && state.allFuelsHistory.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .padding(top = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
            } else {
                val brentColor = MaterialTheme.colorScheme.secondary
                val primaryColor = MaterialTheme.colorScheme.primary
                val fuelColors = state.allFuelsHistory.keys.associateWith {
                    ColorHelper.getFuelColor(it) ?: primaryColor
                }

                UnifiedForecastChart(
                    allFuelsHistory = state.allFuelsHistory,
                    brentHistory = state.brentHistory,
                    fuelColors = fuelColors,
                    brentColor = brentColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(top = 16.dp)
                )

                FlowRow(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.allFuelsHistory.keys.sorted().forEach { fuelId ->
                        val color = fuelColors[fuelId] ?: primaryColor
                        val label = when (fuelId) {
                            "gazole" -> "Gazole"
                            "sp95" -> "SP95/E10"
                            "sp98" -> "SP98"
                            "gplc" -> "GPLc"
                            "e85" -> "E85"
                            else -> fuelId
                        }
                        LegendItem(label, color)
                    }
                    if (state.brentHistory.isNotEmpty()) {
                        LegendItem("Brent", brentColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun UnifiedForecastChart(
    allFuelsHistory: Map<String, List<DailyPricePoint>>,
    brentHistory: List<fr.geoking.gaston.fuelforecast.DailyClose>,
    fuelColors: Map<String, Color>,
    brentColor: Color,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    val allDays = (allFuelsHistory.values.flatten().map { it.day } + brentHistory.map { it.day }).distinct().sorted()
    if (allDays.isEmpty()) return

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val denom = (max(2, allDays.size) - 1).coerceAtLeast(1)

        fun xFor(day: String): Float {
            val i = allDays.indexOf(day)
            if (i < 0) return 0f
            return w * (i / denom.toFloat())
        }

        drawLine(gridColor, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), strokeWidth = 1f)

        if (brentHistory.size >= 2) {
            val bSorted = brentHistory.sortedBy { it.day }
            val prices = bSorted.map { it.close }
            val minP = prices.minOrNull() ?: 0.0
            val maxP = prices.maxOrNull() ?: 1.0
            val range = (maxP - minP).coerceAtLeast(0.001)

            val path = Path()
            bSorted.forEachIndexed { i, pt ->
                val x = xFor(pt.day)
                val norm = ((pt.close - minP) / range).coerceIn(0.0, 1.0)
                val y = h - norm.toFloat() * h
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, brentColor, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
        }

        allFuelsHistory.forEach { (fuelId, history) ->
            if (history.size >= 2) {
                val color = fuelColors[fuelId] ?: Color.Gray
                val sorted = history.sortedBy { it.day }
                val prices = sorted.map { it.priceEurPerL }
                val minP = prices.minOrNull() ?: 0.0
                val maxP = prices.maxOrNull() ?: 1.0
                val range = (maxP - minP).coerceAtLeast(0.001)

                val path = Path()
                sorted.forEachIndexed { i, pt ->
                    val x = xFor(pt.day)
                    val norm = ((pt.priceEurPerL - minP) / range).coerceIn(0.0, 1.0)
                    val y = h - norm.toFloat() * h
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color, style = Stroke(width = 3.5f, cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
fun FuelForecastChartCard(
    state: FuelForecastUiState,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Fuel price outlook (rule-based)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Near-you average vs Brent/heating oil/EUR-USD (Stooq). Not financial advice.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val fuelLabel = when (state.fuelId) {
                "gazole" -> "Gazole"
                "sp95" -> "SP95 / E10"
                "sp98" -> "SP98"
                "gplc" -> "GPLc"
                "e85" -> "E85"
                else -> state.fuelId
            }
            Text(
                "Fuel: $fuelLabel",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp)
            )

            when {
                isLoading -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .padding(top = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(8.dp), strokeWidth = 2.dp)
                    }
                }
                state.errorMessage != null -> Text(
                    state.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp)
                )
                else -> {
                    ForecastSparkline(
                        history = state.historyPoints,
                        forecast = state.forecastPoints,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .padding(top = 12.dp)
                    )
                    val dir = state.directionUp
                    val score = state.marketScore
                    if (dir != null && score != null) {
                        Text(
                            "Signal: ${if (dir) "upward pressure" else "no strong upward signal"} " +
                                "(score ${String.format(Locale.US, "%+.4f", score)})",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    val hit = state.accuracyHitRate7d
                    val mae = state.accuracyMae7d
                    if (hit != null && !hit.isNaN()) {
                        val maeStr = if (mae != null && !mae.isNaN()) {
                            String.format(Locale.US, "%.3f", mae)
                        } else "—"
                        Text(
                            "7d accuracy (when targets realized): hit rate ${String.format(Locale.US, "%.0f", hit * 100)}% · MAE $maeStr €/L",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    val last = state.lastScoreDirectionCorrect
                    if (last != null) {
                        Text(
                            "Last scored prediction: ${if (last) "direction OK" else "direction miss"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ForecastSparkline(
    history: List<DailyPricePoint>,
    forecast: List<DailyPricePoint>,
    modifier: Modifier = Modifier
) {
    val histColor = MaterialTheme.colorScheme.primary
    val foreColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)

    val histSorted = history.sortedBy { it.day }
    val foreSorted = forecast.sortedBy { it.day }
    val allPrices = histSorted.map { it.priceEurPerL } + foreSorted.map { it.priceEurPerL }
    val yMin = allPrices.minOrNull() ?: 1.5
    val yMax = allPrices.maxOrNull() ?: 2.0
    val pad = max(0.02, (yMax - yMin) * 0.12)
    val ymin = yMin - pad
    val ymax = yMax + pad

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val total = histSorted.size + foreSorted.size
        val denom = (max(2, total) - 1).coerceAtLeast(1)

        fun xFor(globalIndex: Int): Float {
            if (total <= 1) return w / 2f
            return w * (globalIndex / denom.toFloat()).coerceIn(0f, 1f)
        }
        fun yFor(p: Double): Float {
            val t = ((p - ymin) / (ymax - ymin)).coerceIn(0.0, 1.0)
            return h - t.toFloat() * h
        }

        drawLine(gridColor, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), strokeWidth = 1f)

        if (histSorted.size >= 2) {
            val path = Path()
            histSorted.forEachIndexed { i, pt ->
                val x = xFor(i)
                val y = yFor(pt.priceEurPerL)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, histColor, style = Stroke(width = 3f, cap = StrokeCap.Round))
            histSorted.forEachIndexed { i, pt ->
                drawCircle(histColor, radius = 4f, center = Offset(xFor(i), yFor(pt.priceEurPerL)))
            }
        } else if (histSorted.size == 1) {
            drawCircle(histColor, radius = 5f, center = Offset(xFor(0), yFor(histSorted[0].priceEurPerL)))
        }

        if (foreSorted.isNotEmpty()) {
            val pathF = Path()
            var started = false
            val startG = max(0, histSorted.size - 1)
            if (histSorted.isNotEmpty()) {
                pathF.moveTo(xFor(startG), yFor(histSorted.last().priceEurPerL))
                started = true
            }
            foreSorted.forEachIndexed { i, pt ->
                val g = histSorted.size + i
                val x = xFor(g)
                val y = yFor(pt.priceEurPerL)
                if (!started) {
                    pathF.moveTo(x, y)
                    started = true
                } else {
                    pathF.lineTo(x, y)
                }
            }
            drawPath(pathF, foreColor, style = Stroke(width = 3f, cap = StrokeCap.Round))
            foreSorted.forEachIndexed { i, pt ->
                val g = histSorted.size + i
                drawCircle(foreColor, radius = 5f, center = Offset(xFor(g), yFor(pt.priceEurPerL)))
            }
        }
    }
}
