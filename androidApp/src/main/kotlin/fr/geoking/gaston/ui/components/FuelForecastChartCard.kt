package fr.geoking.gaston.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.geoking.gaston.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.BoxWithConstraints
import fr.geoking.gaston.repository.DailyPricePoint
import fr.geoking.gaston.repository.FuelForecastUiState
import fr.geoking.gaston.ui.ColorHelper
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private val X_AXIS_HEIGHT = 24.dp

@Composable
private fun fuelTypeLabel(fuelId: String): String = when (fuelId) {
    "gazole" -> stringResource(R.string.fuel_gazole)
    "sp95" -> stringResource(R.string.fuel_sp95)
    "sp98" -> stringResource(R.string.fuel_sp98)
    "gplc" -> stringResource(R.string.fuel_gplc)
    "e85" -> stringResource(R.string.fuel_e85)
    else -> fuelId
}

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
    states: Map<String, FuelForecastUiState>,
    selectedFuelIds: Set<String>,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val unified = states["unified"]
    val brentHistory = unified?.brentHistory ?: emptyList()

    val filteredHistory = unified?.allFuelsHistory?.filterKeys { it in selectedFuelIds } ?: emptyMap()
    val filteredForecast = unified?.allFuelsForecast?.filterKeys { it in selectedFuelIds } ?: emptyMap()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.forecast_price_comparison),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                stringResource(R.string.forecast_relative_trends),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.forecast_chart_projection_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (isLoading && filteredHistory.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(top = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
            } else {
                val brentColor = MaterialTheme.colorScheme.secondary
                val primaryColor = MaterialTheme.colorScheme.primary
                val fuelColors = (filteredHistory.keys + filteredForecast.keys).associateWith {
                    ColorHelper.getFuelColor(it) ?: primaryColor
                }

                UnifiedForecastChart(
                    allFuelsHistory = filteredHistory,
                    allFuelsForecast = filteredForecast,
                    brentHistory = brentHistory,
                    fuelColors = fuelColors,
                    brentColor = brentColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .padding(top = 16.dp)
                )

                FlowRow(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (filteredHistory.keys + filteredForecast.keys).sorted().forEach { fuelId ->
                        val color = fuelColors[fuelId] ?: primaryColor
                        LegendItem(fuelTypeLabel(fuelId), color)
                    }
                    if (filteredForecast.values.any { it.isNotEmpty() }) {
                        LegendDashedItem(
                            label = stringResource(R.string.forecast_legend_projection),
                            color = primaryColor
                        )
                    }
                    if (brentHistory.isNotEmpty()) {
                        LegendDashedItem(
                            label = stringResource(R.string.forecast_legend_brent),
                            color = brentColor
                        )
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
private fun LegendDashedItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 18.dp, height = 10.dp)) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private data class ChartPoint(
    val fuelId: String?, // null if brent
    val day: String,
    val price: Double,
    val isForecast: Boolean,
    val x: Float,
    val y: Float
)

@Composable
private fun UnifiedForecastChart(
    allFuelsHistory: Map<String, List<DailyPricePoint>>,
    allFuelsForecast: Map<String, List<DailyPricePoint>>,
    brentHistory: List<fr.geoking.gaston.fuelforecast.DailyClose>,
    fuelColors: Map<String, Color>,
    brentColor: Color,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val labelStyle = TextStyle(fontSize = 10.sp, color = onSurface.copy(alpha = 0.6f))
    val bubbleBg = MaterialTheme.colorScheme.secondaryContainer
    val bubbleOnBg = MaterialTheme.colorScheme.onSecondaryContainer
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    var selectedPoint by remember { mutableStateOf<ChartPoint?>(null) }

    val allFuelPoints = (allFuelsHistory.values.flatten() + allFuelsForecast.values.flatten())
    val allDays = (allFuelPoints.map { it.day } + brentHistory.map { it.day }).distinct().sorted()
    if (allDays.isEmpty()) return

    val fuelPrices = allFuelPoints.map { it.priceEurPerL }
    val fuelMin = (fuelPrices.minOrNull() ?: 1.5)
    val fuelMax = (fuelPrices.maxOrNull() ?: 2.0)
    val fuelRange = (fuelMax - fuelMin).coerceAtLeast(0.01)
    val yMin = fuelMin - fuelRange * 0.1
    val yMax = fuelMax + fuelRange * 0.1
    val yRange = (yMax - yMin).coerceAtLeast(0.01)

    val brentPrices = brentHistory.map { it.close }
    val brentMin = brentPrices.minOrNull() ?: 0.0
    val brentMax = brentPrices.maxOrNull() ?: 1.0
    val brentRange = (brentMax - brentMin).coerceAtLeast(0.001)

    val yAxisWidth = 40.dp

    BoxWithConstraints(modifier = modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()

        Canvas(modifier = Modifier.fillMaxSize().pointerInput(allDays, allFuelsHistory, allFuelsForecast, brentHistory) {
            detectTapGestures { offset ->
                val w = size.width
                val h = size.height
                val chartW = w - yAxisWidth.toPx()
                val chartH = h - X_AXIS_HEIGHT.toPx()
                val denom = (max(2, allDays.size) - 1).coerceAtLeast(1)

                fun xFor(day: String): Float = yAxisWidth.toPx() + chartW * (allDays.indexOf(day) / denom.toFloat())
                fun yForFuel(p: Double): Float = chartH - (((p - yMin) / yRange).coerceIn(0.0, 1.0).toFloat() * chartH)
                fun yForBrent(p: Double): Float = chartH - (((p - brentMin) / brentRange).coerceIn(0.0, 1.0).toFloat() * chartH)

                val points = mutableListOf<ChartPoint>()
                allFuelsHistory.forEach { (fid, pts) ->
                    pts.forEach { points.add(ChartPoint(fid, it.day, it.priceEurPerL, false, xFor(it.day), yForFuel(it.priceEurPerL))) }
                }
                allFuelsForecast.forEach { (fid, pts) ->
                    pts.forEach { points.add(ChartPoint(fid, it.day, it.priceEurPerL, true, xFor(it.day), yForFuel(it.priceEurPerL))) }
                }
                brentHistory.forEach { points.add(ChartPoint(null, it.day, it.close, false, xFor(it.day), yForBrent(it.close))) }

                selectedPoint = points.minByOrNull { abs(it.x - offset.x) + abs(it.y - offset.y) }?.let {
                    if (abs(it.x - offset.x) < 100 && abs(it.y - offset.y) < 100) it else null
                }
            }
        }) {
            val w = size.width
            val h = size.height
            val chartW = w - yAxisWidth.toPx()
            val chartH = h - X_AXIS_HEIGHT.toPx()
            val denom = (max(2, allDays.size) - 1).coerceAtLeast(1)

            fun xFor(day: String): Float = yAxisWidth.toPx() + chartW * (allDays.indexOf(day) / denom.toFloat())
            fun yForFuel(p: Double): Float = chartH - (((p - yMin) / yRange).coerceIn(0.0, 1.0).toFloat() * chartH)
            fun yForBrent(p: Double): Float = chartH - (((p - brentMin) / brentRange).coerceIn(0.0, 1.0).toFloat() * chartH)

            // Grid & Y-axis labels
            val steps = 5
            for (i in 0..steps) {
                val ratio = i / steps.toFloat()
                val y = chartH - ratio * chartH
                val price = yMin + ratio * yRange
                drawLine(gridColor, Offset(yAxisWidth.toPx(), y), Offset(w, y), strokeWidth = 1f)
                drawText(
                    textMeasurer,
                    String.format(Locale.US, "%.2f", price),
                    Offset(4f, y - 12f),
                    style = labelStyle
                )
            }

            // X-axis labels
            val xLabelsIndices = if (allDays.size >= 3) {
                listOf(0, allDays.size / 2, allDays.size - 1)
            } else {
                allDays.indices.toList()
            }

            xLabelsIndices.forEach { idx ->
                val day = allDays[idx]
                val x = xFor(day)
                val label = day.substring(5).replace("-", "/")
                val textLayoutResult = textMeasurer.measure(label, labelStyle)
                drawText(
                    textLayoutResult,
                    topLeft = Offset(x - textLayoutResult.size.width / 2f, chartH + 4.dp.toPx())
                )
                // Vertical grid line
                drawLine(gridColor, Offset(x, 0f), Offset(x, chartH), strokeWidth = 1f)
            }

            // Brent
            if (brentHistory.size >= 2) {
                val path = Path()
                brentHistory.sortedBy { it.day }.forEachIndexed { i, pt ->
                    val x = xFor(pt.day)
                    val y = yForBrent(pt.close)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, brentColor, style = Stroke(width = 2f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
            }

            // Fuels
            val allFids = (allFuelsHistory.keys + allFuelsForecast.keys).distinct()
            allFids.forEach { fid ->
                val color = fuelColors[fid] ?: Color.Gray
                val hist = allFuelsHistory[fid]?.sortedBy { it.day } ?: emptyList()
                val fore = allFuelsForecast[fid]?.sortedBy { it.day } ?: emptyList()

                if (hist.size >= 2) {
                    val path = Path()
                    hist.forEachIndexed { i, pt ->
                        val x = xFor(pt.day)
                        val y = yForFuel(pt.priceEurPerL)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, color, style = Stroke(width = 3f, cap = StrokeCap.Round))
                }

                if (fore.isNotEmpty()) {
                    val path = Path()
                    var started = false
                    hist.lastOrNull()?.let {
                        path.moveTo(xFor(it.day), yForFuel(it.priceEurPerL))
                        started = true
                    }
                    fore.sortedBy { it.day }.forEach { pt ->
                        val x = xFor(pt.day)
                        val y = yForFuel(pt.priceEurPerL)
                        if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
                    }
                    drawPath(path, color, style = Stroke(width = 3f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
                }
            }

            // Selection marker
            selectedPoint?.let { pt ->
                drawCircle(pt.fuelId?.let { fuelColors[it] } ?: brentColor, radius = 6f, center = Offset(pt.x, pt.y))
            }
        }

        // Detail Bubble
        selectedPoint?.let { pt ->
            val fuelName = pt.fuelId?.let { fuelTypeLabel(it) } ?: stringResource(R.string.forecast_legend_brent)
            val priceStr = if (pt.fuelId != null) "€%.3f".format(pt.price) else "$%.2f".format(pt.price)
            val text = "$fuelName\n$priceStr\n${pt.day}"

            val density = androidx.compose.ui.platform.LocalDensity.current
            val xDp = with(density) { pt.x.toDp() }
            val yDp = with(density) { pt.y.toDp() }

            Box(
                modifier = Modifier
                    .offset(
                        x = if (pt.x > w * 0.7f) xDp - 120.dp else xDp,
                        y = if (pt.y > h * 0.5f) yDp - 80.dp else yDp
                    )
                    .align(Alignment.TopStart)
            ) {
                Surface(
                    color = bubbleBg,
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 4.dp,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = text,
                        modifier = Modifier.padding(8.dp),
                        style = TextStyle(fontSize = 12.sp, color = bubbleOnBg),
                        lineHeight = 16.sp
                    )
                }
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
                stringResource(R.string.forecast_rule_based_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                stringResource(R.string.forecast_chart_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val fuelLabel = fuelTypeLabel(state.fuelId)
            Text(
                stringResource(R.string.forecast_fuel_label, fuelLabel),
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
                            if (dir) {
                                stringResource(
                                    R.string.forecast_signal_up,
                                    String.format(Locale.US, "%+.4f", score)
                                )
                            } else {
                                stringResource(
                                    R.string.forecast_signal_flat,
                                    String.format(Locale.US, "%+.4f", score)
                                )
                            },
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
                            stringResource(
                                R.string.forecast_accuracy_phone,
                                "${String.format(Locale.US, "%.0f", hit * 100)}%",
                                maeStr
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    val last = state.lastScoreDirectionCorrect
                    if (last != null) {
                        Text(
                            if (last) {
                                stringResource(R.string.forecast_last_scored_ok)
                            } else {
                                stringResource(R.string.forecast_last_scored_miss)
                            },
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
    val textMeasurer = rememberTextMeasurer()
    val histColor = MaterialTheme.colorScheme.primary
    val foreColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val labelStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))

    val histSorted = history.sortedBy { it.day }
    val foreSorted = forecast.sortedBy { it.day }
    val allPrices = histSorted.map { it.priceEurPerL } + foreSorted.map { it.priceEurPerL }
    val allDays = (histSorted + foreSorted).map { it.day }
    val yMin = allPrices.minOrNull() ?: 1.5
    val yMax = allPrices.maxOrNull() ?: 2.0
    val pad = max(0.02, (yMax - yMin) * 0.12)
    val ymin = yMin - pad
    val ymax = yMax + pad

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val chartH = h - X_AXIS_HEIGHT.toPx()
        val total = histSorted.size + foreSorted.size
        val denom = (max(2, total) - 1).coerceAtLeast(1)

        fun xFor(globalIndex: Int): Float {
            if (total <= 1) return w / 2f
            return w * (globalIndex / denom.toFloat()).coerceIn(0f, 1f)
        }
        fun yFor(p: Double): Float {
            val t = ((p - ymin) / (ymax - ymin)).coerceIn(0.0, 1.0)
            return chartH - t.toFloat() * chartH
        }

        drawLine(gridColor, Offset(0f, chartH * 0.5f), Offset(w, chartH * 0.5f), strokeWidth = 1f)

        // X-axis labels
        if (allDays.isNotEmpty()) {
            val xLabelsIndices = if (allDays.size >= 3) {
                listOf(0, allDays.size / 2, allDays.size - 1)
            } else {
                allDays.indices.toList()
            }
            xLabelsIndices.forEach { idx ->
                val day = allDays[idx]
                val x = xFor(idx)
                val label = day.substring(5).replace("-", "/")
                val textLayoutResult = textMeasurer.measure(label, labelStyle)
                drawText(
                    textLayoutResult,
                    topLeft = Offset(x - textLayoutResult.size.width / 2f, chartH + 4.dp.toPx())
                )
                // Vertical grid line
                drawLine(gridColor, Offset(x, 0f), Offset(x, chartH), strokeWidth = 1f)
            }
        }

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
