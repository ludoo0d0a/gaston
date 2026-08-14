package fr.geoking.gaston.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.geoking.gaston.R
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.ui.BrandHelper
import fr.geoking.gaston.ui.ColorHelper
import fr.geoking.gaston.poi.MapPoiFilter
import kotlin.math.roundToInt

@Composable
fun CheapestStationsCard(
    stations: List<Poi>,
    userLatitude: Double?,
    userLongitude: Double?,
    selectedEnergyIds: Set<String>,
    onClick: (Poi) -> Unit,
    onMapClick: () -> Unit,
    modifier: Modifier = Modifier,
    emptyMessage: String? = null,
    title: String? = null,
    isLoading: Boolean = false
) {
    val fuelIds = selectedEnergyIds - "electric"
    val minPrice = remember(stations, fuelIds) {
        CheapestStationHighlight.minFuelPrice(stations, fuelIds)
    }

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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title ?: "Nearby cheapest",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onMapClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_map),
                        contentDescription = stringResource(R.string.action_open_map),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (stations.isEmpty()) {
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                } else {
                    Text(
                        text = emptyMessage ?: stringResource(R.string.poi_no_pois_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = if (isLoading) Modifier.alpha(0.5f) else Modifier
                    ) {
                        stations.forEach { poi ->
                            val isCheapest = CheapestStationHighlight.isCheapestFuelStation(
                                poi = poi,
                                minPrice = minPrice,
                                fuelIds = fuelIds
                            )
                            CheapestHighlightCard(
                                isCheapest = isCheapest,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CheapestStationItem(
                                    poi = poi,
                                    userLatitude = userLatitude,
                                    userLongitude = userLongitude,
                                    selectedEnergyIds = selectedEnergyIds,
                                    isCheapest = isCheapest,
                                    onClick = { onClick(poi) }
                                )
                            }
                        }
                    }
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
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
    isCheapest: Boolean,
    onClick: () -> Unit
) {
    val brandInfo = BrandHelper.getBrandInfo(poi.brand)
    val distance = if (userLatitude != null && userLongitude != null) {
        approxDistanceKm(userLatitude, userLongitude, poi.latitude, poi.longitude)
    } else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val resId = when {
            brandInfo != null -> brandInfo.iconResId
            poi.poiCategory == PoiCategory.Parking -> R.drawable.ic_poi_parking
            poi.poiCategory == PoiCategory.Toilet -> R.drawable.ic_poi_toilet
            poi.poiCategory == PoiCategory.DrinkingWater -> R.drawable.ic_poi_water
            poi.poiCategory == PoiCategory.Camping -> R.drawable.ic_poi_camping
            poi.poiCategory == PoiCategory.CaravanSite -> R.drawable.ic_poi_caravan
            poi.poiCategory == PoiCategory.PicnicSite -> R.drawable.ic_poi_picnic
            poi.poiCategory == PoiCategory.Radar -> R.drawable.ic_poi_radar
            poi.poiCategory == PoiCategory.Viewpoint -> R.drawable.ic_poi_viewpoint
            poi.poiCategory == PoiCategory.PostBox -> R.drawable.ic_poi_post_box
            poi.poiCategory == PoiCategory.WaterBody -> R.drawable.ic_poi_water_body
            poi.poiCategory == PoiCategory.Cafe -> R.drawable.ic_poi_cafe
            poi.poiCategory == PoiCategory.Supermarket -> R.drawable.ic_poi_supermarket
            poi.poiCategory == PoiCategory.Restaurant -> R.drawable.ic_poi_restaurant
            poi.poiCategory == PoiCategory.FastFood -> R.drawable.ic_poi_fast_food
            poi.poiCategory == PoiCategory.TruckStop -> R.drawable.ic_poi_truck
            poi.poiCategory == PoiCategory.RestArea -> R.drawable.ic_poi_rest_area
            poi.isElectric -> R.drawable.ic_poi_electric
            else -> R.drawable.ic_poi_gas
        }
        Icon(
            painter = painterResource(id = resId),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = if (brandInfo != null) Color.Unspecified else MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = poi.name.ifBlank { poi.siteName ?: "Station" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
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
            if (!poi.isElectric) {
                val prices = poi.fuelPrices.orEmpty()
                val matchingPrices = if (fuelIds.isEmpty()) {
                    prices
                } else {
                    prices.filter { MapPoiFilter.fuelNameToId(it.fuelName) in fuelIds }
                }

                val bestPrice = matchingPrices.minByOrNull { it.price }
                if (bestPrice != null) {
                    val formattedPrice = "€%.3f".format(bestPrice.price)
                    if (isCheapest) {
                        CheapestStationBadge(text = formattedPrice)
                    } else {
                        Text(
                            text = formattedPrice,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF16A34A)
                        )
                    }
                    val fuelId = MapPoiFilter.fuelNameToId(bestPrice.fuelName)
                    Text(
                        text = bestPrice.fuelName,
                        style = MaterialTheme.typography.labelSmall,
                        color = fuelId?.let { ColorHelper.getFuelColor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (fuelIds.isNotEmpty()) {
                    // Fuel selected but no matching price at this station: keep station visible and show placeholder.
                    Text(
                        text = "—",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
