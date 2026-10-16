package fr.geoking.gaston.ui.components

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
import androidx.compose.ui.draw.drawWithCache
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
    "sp95_e10" -> stringResource(R.string.fuel_sp95_e10)
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

            val brentColor = MaterialTheme.colorScheme.secondary
            val primaryColor = MaterialTheme.colorScheme.primary
            val fuelColors = (filteredHistory.keys + filteredForecast.keys).associateWith {
                ColorHelper.getFuelColor(it) ?: primaryColor
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                UnifiedForecastChart(
                    allFuelsHistory = filteredHistory,
                    allFuelsForecast = filteredForecast,
                    brentHistory = brentHistory,
                    fuelColors = fuelColors,
                    brentColor = brentColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                )

                if (isLoading && filteredHistory.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                }
            }

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
        Box(
            modifier = Modifier
                .size(width = 18.dp, height = 10.dp)
                .drawWithCache {
                    val y = size.height / 2f
                    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                    onDrawBehind {
                        drawLine(
                            color = color,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 3f,
                            pathEffect = dash
                        )
                    }
                }
        )
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
        .ifEmpty {
            // Keep axes visible when there is no series yet.
            val today = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Paris"))
            (0..6).map { today.minusDays((6 - it).toLong()).toString() }
        }

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

    val dayIndex = remember(allDays) { allDays.withIndex().associate { it.value to it.index } }
    val brentSorted = remember(brentHistory) { brentHistory.sortedBy { it.day } }
    val allFids = remember(allFuelsHistory, allFuelsForecast) {
        (allFuelsHistory.keys + allFuelsForecast.keys).distinct()
    }
    val histByFuel = remember(allFuelsHistory) {
        allFuelsHistory.mapValues { (_, pts) -> pts.sortedBy { it.day } }
    }
    val foreByFuel = remember(allFuelsForecast) {
        allFuelsForecast.mapValues { (_, pts) -> pts.sortedBy { it.day } }
    }

    BoxWithConstraints(modifier = modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(allDays, allFuelsHistory, allFuelsForecast, brentHistory, dayIndex) {
                    detectTapGestures { offset ->
                        val chartW = size.width - yAxisWidth.toPx()
                        val chartH = size.height - X_AXIS_HEIGHT.toPx()
                        val denom = (max(2, allDays.size) - 1).coerceAtLeast(1)

                        fun xFor(day: String): Float {
                            val idx = dayIndex[day] ?: return 0f
                            return yAxisWidth.toPx() + chartW * (idx / denom.toFloat())
                        }
                        fun yForFuel(p: Double): Float =
                            chartH - (((p - yMin) / yRange).coerceIn(0.0, 1.0).toFloat() * chartH)
                        fun yForBrent(p: Double): Float =
                            chartH - (((p - brentMin) / brentRange).coerceIn(0.0, 1.0).toFloat() * chartH)

                        val points = mutableListOf<ChartPoint>()
                        allFuelsHistory.forEach { (fid, pts) ->
                            pts.forEach {
                                points.add(
                                    ChartPoint(fid, it.day, it.priceEurPerL, false, xFor(it.day), yForFuel(it.priceEurPerL))
                                )
                            }
                        }
                        allFuelsForecast.forEach { (fid, pts) ->
                            pts.forEach {
                                points.add(
                                    ChartPoint(fid, it.day, it.priceEurPerL, true, xFor(it.day), yForFuel(it.priceEurPerL))
                                )
                            }
                        }
                        brentHistory.forEach {
                            points.add(ChartPoint(null, it.day, it.close, false, xFor(it.day), yForBrent(it.close)))
                        }

                        selectedPoint = points.minByOrNull { abs(it.x - offset.x) + abs(it.y - offset.y) }?.let {
                            if (abs(it.x - offset.x) < 100 && abs(it.y - offset.y) < 100) it else null
                        }
                    }
                }
                .drawWithCache {
                    val chartW = size.width - yAxisWidth.toPx()
                    val chartH = size.height - X_AXIS_HEIGHT.toPx()
                    val denom = (max(2, allDays.size) - 1).coerceAtLeast(1)
                    val yAxisPx = yAxisWidth.toPx()
                    val xLabelY = chartH + 4.dp.toPx()
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))

                    fun xFor(day: String): Float {
                        val idx = dayIndex[day] ?: return 0f
                        return yAxisPx + chartW * (idx / denom.toFloat())
                    }
                    fun yForFuel(p: Double): Float =
                        chartH - (((p - yMin) / yRange).coerceIn(0.0, 1.0).toFloat() * chartH)
                    fun yForBrent(p: Double): Float =
                        chartH - (((p - brentMin) / brentRange).coerceIn(0.0, 1.0).toFloat() * chartH)

                    val steps = 5
                    val yGridYs = FloatArray(steps + 1)
                    val yGridPrices = DoubleArray(steps + 1)
                    for (i in 0..steps) {
                        val ratio = i / steps.toFloat()
                        yGridYs[i] = chartH - ratio * chartH
                        yGridPrices[i] = yMin + ratio * yRange
                    }

                    val xLabelsIndices = if (allDays.size >= 3) {
                        listOf(0, allDays.size / 2, allDays.size - 1)
                    } else {
                        allDays.indices.toList()
                    }
                    val xLabelLayouts = xLabelsIndices.map { idx ->
                        val day = allDays[idx]
                        val label = day.substring(5).replace("-", "/")
                        Triple(xFor(day), textMeasurer.measure(label, labelStyle), day)
                    }

                    val brentPath = if (brentSorted.size >= 2) {
                        Path().also { path ->
                            brentSorted.forEachIndexed { i, pt ->
                                val x = xFor(pt.day)
                                val y = yForBrent(pt.close)
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                        }
                    } else null

                    val fuelPaths = allFids.map { fid ->
                        val color = fuelColors[fid] ?: Color.Gray
                        val hist = histByFuel[fid].orEmpty()
                        val fore = foreByFuel[fid].orEmpty()
                        val histPath = if (hist.size >= 2) {
                            Path().also { path ->
                                hist.forEachIndexed { i, pt ->
                                    val x = xFor(pt.day)
                                    val y = yForFuel(pt.priceEurPerL)
                                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }
                            }
                        } else null
                        val forePath = if (fore.isNotEmpty()) {
                            Path().also { path ->
                                var started = false
                                hist.lastOrNull()?.let {
                                    path.moveTo(xFor(it.day), yForFuel(it.priceEurPerL))
                                    started = true
                                }
                                fore.forEach { pt ->
                                    val x = xFor(pt.day)
                                    val y = yForFuel(pt.priceEurPerL)
                                    if (!started) {
                                        path.moveTo(x, y)
                                        started = true
                                    } else {
                                        path.lineTo(x, y)
                                    }
                                }
                            }
                        } else null
                        Triple(color, histPath, forePath)
                    }

                    val histStroke = Stroke(width = 3f, cap = StrokeCap.Round)
                    val brentStroke = Stroke(width = 2f, cap = StrokeCap.Round, pathEffect = dashEffect)
                    val foreStroke = Stroke(width = 3f, cap = StrokeCap.Round, pathEffect = dashEffect)

                    onDrawBehind {
                        for (i in 0..steps) {
                            val y = yGridYs[i]
                            drawLine(gridColor, Offset(yAxisPx, y), Offset(size.width, y), strokeWidth = 1f)
                            drawText(
                                textMeasurer,
                                String.format(Locale.US, "%.2f", yGridPrices[i]),
                                Offset(4f, y - 12f),
                                style = labelStyle
                            )
                        }

                        xLabelLayouts.forEach { (x, layout, _) ->
                            drawText(
                                layout,
                                topLeft = Offset(x - layout.size.width / 2f, xLabelY)
                            )
                            drawLine(gridColor, Offset(x, 0f), Offset(x, chartH), strokeWidth = 1f)
                        }

                        brentPath?.let { drawPath(it, brentColor, style = brentStroke) }

                        fuelPaths.forEach { (color, historyPath, forecastPath) ->
                            historyPath?.let { drawPath(it, color, style = histStroke) }
                            forecastPath?.let { drawPath(it, color, style = foreStroke) }
                        }

                        selectedPoint?.let { pt ->
                            drawCircle(
                                pt.fuelId?.let { fuelColors[it] } ?: brentColor,
                                radius = 6f,
                                center = Offset(pt.x, pt.y)
                            )
                        }
                    }
                }
        )

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

    Box(
        modifier = modifier.drawWithCache {
            val w = size.width
            val chartH = size.height - X_AXIS_HEIGHT.toPx()
            val total = histSorted.size + foreSorted.size
            val denom = (max(2, total) - 1).coerceAtLeast(1)
            val xLabelY = chartH + 4.dp.toPx()
            val stroke = Stroke(width = 3f, cap = StrokeCap.Round)

            fun xFor(globalIndex: Int): Float {
                if (total <= 1) return w / 2f
                return w * (globalIndex / denom.toFloat()).coerceIn(0f, 1f)
            }
            fun yFor(p: Double): Float {
                val t = ((p - ymin) / (ymax - ymin)).coerceIn(0.0, 1.0)
                return chartH - t.toFloat() * chartH
            }

            val xLabelsIndices = if (allDays.isNotEmpty()) {
                if (allDays.size >= 3) listOf(0, allDays.size / 2, allDays.size - 1)
                else allDays.indices.toList()
            } else emptyList()
            val xLabelLayouts = xLabelsIndices.map { idx ->
                val day = allDays[idx]
                val label = day.substring(5).replace("-", "/")
                xFor(idx) to textMeasurer.measure(label, labelStyle)
            }

            val histPath = if (histSorted.size >= 2) {
                Path().also { path ->
                    histSorted.forEachIndexed { i, pt ->
                        val x = xFor(i)
                        val y = yFor(pt.priceEurPerL)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                }
            } else null

            val histDots = if (histSorted.size >= 2) {
                histSorted.mapIndexed { i, pt -> Offset(xFor(i), yFor(pt.priceEurPerL)) }
            } else if (histSorted.size == 1) {
                listOf(Offset(xFor(0), yFor(histSorted[0].priceEurPerL)))
            } else emptyList()

            val forePath = if (foreSorted.isNotEmpty()) {
                Path().also { pathF ->
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
                }
            } else null

            val foreDots = foreSorted.mapIndexed { i, pt ->
                val g = histSorted.size + i
                Offset(xFor(g), yFor(pt.priceEurPerL))
            }

            onDrawBehind {
                drawLine(gridColor, Offset(0f, chartH * 0.5f), Offset(w, chartH * 0.5f), strokeWidth = 1f)

                xLabelLayouts.forEach { (x, layout) ->
                    drawText(layout, topLeft = Offset(x - layout.size.width / 2f, xLabelY))
                    drawLine(gridColor, Offset(x, 0f), Offset(x, chartH), strokeWidth = 1f)
                }

                histPath?.let { drawPath(it, histColor, style = stroke) }
                histDots.forEach { center ->
                    drawCircle(histColor, radius = if (histSorted.size == 1) 5f else 4f, center = center)
                }

                forePath?.let { drawPath(it, foreColor, style = stroke) }
                foreDots.forEach { center ->
                    drawCircle(foreColor, radius = 5f, center = center)
                }
            }
        }
    )
}
