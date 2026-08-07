package fr.geoking.gaston.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.gaston.R

@Composable
fun PoiDetailRow(label: String, value: Boolean?) {
    if (value == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
        Text(
            if (value) stringResource(R.string.label_yes) else stringResource(R.string.label_no),
            color = if (value) Color(0xFF22C55E) else Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp
        )
    }
}

