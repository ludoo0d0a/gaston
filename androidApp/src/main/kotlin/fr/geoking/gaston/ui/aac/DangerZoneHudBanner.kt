package fr.geoking.gaston.ui.aac

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.gaston.R

/**
 * AAC Level A presence indicator: "zone de danger" + EU-style VMA disk when known.
 * No control pin / distance-to-radar — allowed vocabulary for R. 413-15 spirit.
 */
@Composable
fun DangerZoneHudBanner(
    speedLimitKmH: Int?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .background(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = stringResource(R.string.aac_hud_zone_entry),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (speedLimitKmH != null && speedLimitKmH > 0) {
                Text(
                    text = stringResource(R.string.aac_hud_vma, speedLimitKmH),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        if (speedLimitKmH != null && speedLimitKmH > 0) {
            EuSpeedLimitDisk(speedLimitKmH = speedLimitKmH)
        }
    }
}

/** Round speed-limit sign (EU B14 style): white disk, red rim, black number. */
@Composable
fun EuSpeedLimitDisk(
    speedLimitKmH: Int,
    modifier: Modifier = Modifier,
    sizeDp: Int = 56,
) {
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .background(Color.White, CircleShape)
            .border(width = (sizeDp * 0.12f).dp, color = Color(0xFFE30613), shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = speedLimitKmH.toString(),
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = (sizeDp * 0.36f).sp,
            maxLines = 1,
        )
    }
}
