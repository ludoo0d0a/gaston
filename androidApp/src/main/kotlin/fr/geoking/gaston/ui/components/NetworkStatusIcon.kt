package fr.geoking.gaston.ui.components

import fr.geoking.gaston.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.SignalCellularConnectedNoInternet4Bar
import androidx.compose.material.icons.filled.SignalCellularNull
import androidx.compose.material.icons.filled.SignalCellularOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.gaston.shared.network.NetworkStatus
import fr.geoking.gaston.shared.network.NetworkType

@Composable
fun NetworkStatusIcon(
    status: NetworkStatus,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.25f))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Country Code
        status.countryCode?.let {
            Text(
                text = it,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        // Signal Icon
        val icon = when {
            !status.isConnected -> if (status.networkType == NetworkType.WIFI) Icons.Default.WifiOff else Icons.Default.SignalCellularOff
            status.networkType == NetworkType.WIFI -> Icons.Default.Wifi
            status.networkType == NetworkType.NONE -> Icons.Default.SignalCellularNull
            else -> Icons.Default.SignalCellular4Bar
        }

        Icon(
            imageVector = icon,
            contentDescription = stringResource(R.string.network_status),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
            modifier = Modifier.size(14.dp)
        )

        // Network Type (5G, 4G, etc.)
        val typeText = when (status.networkType) {
            NetworkType.FIVE_G -> "5G"
            NetworkType.FOUR_G -> "4G"
            NetworkType.THREE_G -> "3G"
            NetworkType.TWO_G -> "2G"
            NetworkType.EDGE -> "E"
            NetworkType.GPRS -> "G"
            else -> null
        }

        if (typeText != null && status.networkType != NetworkType.WIFI) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = typeText,
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        if (status.isRoaming) {
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "R",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
