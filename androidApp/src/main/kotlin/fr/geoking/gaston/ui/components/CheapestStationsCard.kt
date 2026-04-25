package fr.geoking.gaston.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.gaston.R
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.ui.BrandHelper
import fr.geoking.gaston.ui.ColorHelper
import fr.geoking.gaston.poi.MapPoiFilter
import kotlin.math.roundToInt

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map

@Composable
fun CheapestStationsCard(
    stations: List<Poi>,
    userLatitude: Double?,
    userLongitude: Double?,
    selectedEnergyIds: Set<String>,
    onClick: (Poi) -> Unit,
    onMapClick: () -> Unit,
    modifier: Modifier = Modifier,
    emptyMessage: String? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Nearby cheapest",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = onMapClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = "Open Map",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (stations.isEmpty()) {
                Text(
                    text = emptyMessage ?: "No stations found nearby. Try opening the map to search elsewhere.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                stations.forEachIndexed { index, poi ->
                    CheapestStationItem(
                        poi = poi,
                        userLatitude = userLatitude,
                        userLongitude = userLongitude,
                        selectedEnergyIds = selectedEnergyIds,
                        onClick = { onClick(poi) }
                    )
                    if (index < stations.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CheapestStationItem(
    poi: Poi,
    userLatitude: Double?,
    userLongitude: Double?,
    selectedEnergyIds: Set<String>,
    onClick: () -> Unit
) {
    val brandInfo = BrandHelper.getBrandInfo(poi.brand)
    val distance = if (userLatitude != null && userLongitude != null) {
        approxDistanceKm(userLatitude, userLongitude, poi.latitude, poi.longitude)
    } else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val resId = brandInfo?.iconResId ?: if (poi.isElectric) R.drawable.ic_poi_electric else R.drawable.ic_poi_gas
        Icon(
            painter = painterResource(id = resId),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = if (brandInfo != null) Color.Unspecified else MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = poi.name.ifBlank { poi.siteName ?: "Station" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            distance?.let {
                Text(
                    text = "%.1f km".format(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            val fuelIds = selectedEnergyIds - "electric"

            // Display fuel price if applicable
            if (!poi.fuelPrices.isNullOrEmpty()) {
                val matchingPrices = if (fuelIds.isEmpty()) {
                    poi.fuelPrices!!
                } else {
                    poi.fuelPrices!!.filter { MapPoiFilter.fuelNameToId(it.fuelName) in fuelIds }
                }

                val bestPrice = matchingPrices.minByOrNull { it.price }
                if (bestPrice != null) {
                    Text(
                        text = "€%.3f".format(bestPrice.price),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF16A34A) // Green
                    )
                    val fuelId = MapPoiFilter.fuelNameToId(bestPrice.fuelName)
                    Text(
                        text = bestPrice.fuelName,
                        style = MaterialTheme.typography.labelSmall,
                        color = fuelId?.let { ColorHelper.getFuelColor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Display power if applicable
            if (poi.isElectric && poi.powerKw != null) {
                Text(
                    text = "${poi.powerKw!!.roundToInt()} kW",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ColorHelper.getPowerColor(poi.powerKw!!)
                )
            }
        }
    }
}

private fun approxDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLatKm = (lat2 - lat1) * 111.0
    val avgLatRad = ((lat1 + lat2) / 2.0) * Math.PI / 180.0
    val dLonKm = (lon2 - lon1) * 111.0 * Math.cos(avgLatRad)
    return Math.sqrt(dLatKm * dLatKm + dLonKm * dLonKm)
}
