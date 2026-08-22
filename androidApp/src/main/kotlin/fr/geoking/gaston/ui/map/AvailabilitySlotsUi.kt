package fr.geoking.gaston.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.gaston.R
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import kotlin.math.min

/** Shared slot-bar layout for map markers and detail UI. */
object AvailabilityBarLayout {
    const val MAX_BARS = 5

    private val availableColor = 0xFF22C55E.toInt()
    private val occupiedColor = 0xFF64748B.toInt()

    /** One boolean per bar: `true` = available (green). */
    fun barStates(available: Int, total: Int): List<Boolean> {
        if (total <= 0) return emptyList()
        val barCount = min(total, MAX_BARS)
        if (total <= MAX_BARS) {
            return List(barCount) { index -> index < available.coerceIn(0, total) }
        }
        val greenBars = (available * barCount / total).coerceIn(0, barCount)
        return List(barCount) { index -> index < greenBars }
    }

    fun barColor(available: Boolean): Int = if (available) availableColor else occupiedColor
}

@Composable
fun AvailabilitySlotsRow(
    summary: StationAvailabilitySummary,
    modifier: Modifier = Modifier,
    barHeight: Dp = 4.dp,
    barWidth: Dp = 10.dp,
    barGap: Dp = 2.dp,
    textColor: Color = Color.White.copy(alpha = 0.85f),
    showLabel: Boolean = true,
) {
    val states = AvailabilityBarLayout.barStates(summary.availableCount, summary.totalCount)
    if (states.isEmpty()) return

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(barGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            states.forEach { available ->
                Box(
                    modifier = Modifier
                        .width(barWidth)
                        .height(barHeight)
                        .background(Color(AvailabilityBarLayout.barColor(available))),
                )
            }
        }
        if (showLabel) {
            Text(
                text = pluralStringResource(
                    R.plurals.available_count,
                    summary.availableCount,
                    summary.availableCount,
                    summary.totalCount,
                ),
                color = textColor,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
    }
}
