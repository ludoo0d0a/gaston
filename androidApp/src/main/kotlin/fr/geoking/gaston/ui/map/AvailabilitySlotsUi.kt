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

    val greenColor = 0xFF22C55E.toInt()
    val orangeColor = 0xFFFF9800.toInt()
    val redColor = 0xFFEF4444.toInt()
    val occupiedColor = 0xFF64748B.toInt()

    /**
     * Determines overall availability status color:
     * - Green: > 25% availability remaining
     * - Orange: <= 25% availability remaining (and at least 1 slot)
     * - Red: 0% / complete (0 available slots)
     */
    fun availabilityColor(availableCount: Int, totalCount: Int): Int {
        if (totalCount <= 0 || availableCount <= 0) return redColor
        val ratio = availableCount.toDouble() / totalCount.toDouble()
        return if (ratio > 0.25) greenColor else orangeColor
    }

    /** One boolean per bar: `true` = available slot, `false` = occupied/unavailable slot. */
    fun barStates(available: Int, total: Int): List<Boolean> {
        if (total <= 0) return emptyList()
        val barCount = min(total, MAX_BARS)
        if (total <= MAX_BARS) {
            return List(barCount) { index -> index < available.coerceIn(0, total) }
        }
        val filledBars = (available * barCount / total).coerceIn(0, barCount)
        return List(barCount) { index -> index < filledBars }
    }

    fun barColor(available: Boolean, statusColor: Int = greenColor): Int =
        if (available) statusColor else occupiedColor

    fun barColor(available: Boolean): Int = if (available) greenColor else occupiedColor
}

@Composable
fun AvailabilitySlotsRow(
    summary: StationAvailabilitySummary,
    modifier: Modifier = Modifier,
    barHeight: Dp = 4.dp,
    barWidth: Dp = 10.dp,
    barGap: Dp = 2.dp,
    textColor: Color? = null,
    showLabel: Boolean = true,
) {
    val states = AvailabilityBarLayout.barStates(summary.availableCount, summary.totalCount)
    if (states.isEmpty()) return

    val statusColorInt = AvailabilityBarLayout.availabilityColor(summary.availableCount, summary.totalCount)
    val statusColor = Color(statusColorInt)
    val effectiveTextColor = textColor ?: statusColor

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
                        .background(Color(AvailabilityBarLayout.barColor(available, statusColorInt))),
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
                color = effectiveTextColor,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
    }
}
