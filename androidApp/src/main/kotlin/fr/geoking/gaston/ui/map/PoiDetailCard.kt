package fr.geoking.gaston.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.gaston.R
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.poi.FuelPrice
import fr.geoking.gaston.poi.MapPoiFilter
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.isGenericStationName
import fr.geoking.gaston.ui.BrandHelper
import fr.geoking.gaston.ui.ColorHelper
import kotlin.math.roundToInt

@Composable
fun PoiDetailCard(
    poi: Poi,
    availabilitySummary: StationAvailabilitySummary? = null,
    highlightedFuelIds: Set<String> = emptySet(),
    highlightedPowerLevels: Set<Int> = emptySet(),
    onNavigate: () -> Unit,
    onLocate: () -> Unit,
    onShowDetails: () -> Unit,
    isSelected: Boolean = false,
    isLoggedIn: Boolean = false,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val rawSiteName = poi.siteName?.takeIf { it.isNotBlank() } ?: poi.name
    val isGenericName = isGenericStationName(rawSiteName)
    val brandInfo = BrandHelper.getBrandInfo(poi.brand)
    val locationSummary = buildList {
        listOf(poi.townLocal, poi.postcode).filter { !it.isNullOrBlank() }.joinToString(", ").takeIf { it.isNotBlank() }?.let { add(it) }
        poi.countryLocal?.takeIf { it.isNotBlank() }?.let { add(it) }
    }.joinToString(", ").takeIf { it.isNotBlank() }
    val streetAddress = poi.addressLocal?.takeIf { it.isNotBlank() } ?: poi.address.takeIf { it.isNotBlank() }
    val displayTitle = when {
        !isGenericName -> rawSiteName
        brandInfo != null && !locationSummary.isNullOrBlank() -> "${brandInfo.displayName} – $locationSummary"
        brandInfo != null && !streetAddress.isNullOrBlank() -> "${brandInfo.displayName} – ${streetAddress.take(40)}${if (streetAddress.length > 40) "…" else ""}"
        brandInfo != null -> brandInfo.displayName
        !locationSummary.isNullOrBlank() -> locationSummary
        !streetAddress.isNullOrBlank() -> streetAddress.take(50).let { if (streetAddress.length > 50) "$it…" else it }
        else -> "%.4f, %.4f".format(poi.latitude, poi.longitude)
    }
    val addressLines = buildList {
        if (!streetAddress.isNullOrBlank()) add(streetAddress)
        if (!locationSummary.isNullOrBlank() && locationSummary != streetAddress) add(locationSummary)
        if (isEmpty()) add("%.4f, %.4f".format(poi.latitude, poi.longitude))
    }

    val effectiveCategory = poi.poiCategory ?: if (poi.isElectric) PoiCategory.Irve else PoiCategory.Gas

    @Composable
    fun FuelPricesCompactList(prices: List<FuelPrice>) {
        val sorted = prices.sortedBy { it.fuelName.lowercase() }
        sorted.forEach { fp ->
            val fuelId = MapPoiFilter.fuelNameToId(fp.fuelName)
            val matchColor = fuelId?.let { ColorHelper.getFuelColor(it) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = fp.fuelName,
                    color = matchColor ?: Color.White,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val updatedAt = fp.updatedAt
                    if (updatedAt != null) {
                        Text(
                            text = fr.geoking.gaston.shared.datetime.DateTimeUtils.formatRelativeTime(updatedAt),
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    Text(
                        text = if (fp.outOfStock) "—" else "€%.3f".format(fp.price),
                        color = if (fp.outOfStock) Color.White.copy(alpha = 0.5f) else Color(0xFF22C55E),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    Card(
        modifier = modifier
            .defaultMinSize(minHeight = 160.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1D4ED8) else Color(0xFF0F172A),
            contentColor = Color.White
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onShowDetails() },
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val resId = brandInfo?.iconResId ?: when (poi.poiCategory) {
                        PoiCategory.Parking -> R.drawable.ic_poi_parking
                        PoiCategory.Toilet -> R.drawable.ic_poi_toilet
                        PoiCategory.DrinkingWater -> R.drawable.ic_poi_water
                        PoiCategory.Camping -> R.drawable.ic_poi_camping
                        PoiCategory.CaravanSite -> R.drawable.ic_poi_caravan
                        PoiCategory.PicnicSite -> R.drawable.ic_poi_picnic
                        PoiCategory.Radar -> R.drawable.ic_poi_radar
                        PoiCategory.Viewpoint -> R.drawable.ic_poi_viewpoint
                        PoiCategory.PostBox -> R.drawable.ic_poi_post_box
                        PoiCategory.WaterBody -> R.drawable.ic_poi_water_body
                        PoiCategory.Cafe -> R.drawable.ic_poi_cafe
                        PoiCategory.Supermarket -> R.drawable.ic_poi_supermarket
                        PoiCategory.Restaurant -> R.drawable.ic_poi_restaurant
                        PoiCategory.FastFood -> R.drawable.ic_poi_fast_food
                        PoiCategory.TruckStop -> R.drawable.ic_poi_truck
                        PoiCategory.RestArea -> R.drawable.ic_poi_rest_area
                        else -> if (poi.isElectric) R.drawable.ic_poi_electric else R.drawable.ic_poi_gas
                    }
                    Icon(
                        painter = painterResource(id = resId),
                        contentDescription = brandInfo?.displayName ?: if (poi.isElectric) stringResource(R.string.charging_station) else stringResource(R.string.gas_station),
                        modifier = Modifier.size(32.dp),
                        tint = if (brandInfo != null) Color.Unspecified else Color.White
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayTitle,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        brandInfo?.let { info ->
                            if (isGenericName || !displayTitle.startsWith(info.displayName, ignoreCase = true)) {
                                Text(
                                    text = info.displayName,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (poi.isElectric) {
                            val infoParts = listOfNotNull(
                                if (poi.isOnHighway) stringResource(R.string.highway) else null,
                                poi.chargePointCount?.let { n ->
                                    pluralStringResource(R.plurals.points_count, n, n)
                                },
                            )
                            if (infoParts.isNotEmpty()) {
                                Text(
                                    text = infoParts.joinToString(" • "),
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            availabilitySummary?.let { summary ->
                                Spacer(modifier = Modifier.height(4.dp))
                                AvailabilitySlotsRow(
                                    summary = summary,
                                    textColor = Color.White.copy(alpha = 0.85f),
                                )
                            }

                            // Display power level with its specific color.
                            val powerKw = poi.powerKw
                            if (powerKw != null) {
                                val color = ColorHelper.getPowerColor(powerKw)
                                Text(
                                    text = "${powerKw.roundToInt()} kW",
                                    color = color,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = onNavigate,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Directions,
                            contentDescription = stringResource(R.string.navigate),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        addressLines.take(2).forEach { line ->
                            Text(
                                text = line,
                                color = Color.White,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (isSelected) {
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(6.dp))

                    // Compact “show everything we have” summary for merged POIs.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        when (effectiveCategory) {
                            PoiCategory.Gas -> {
                                val prices = poi.fuelPrices.orEmpty()
                                if (prices.isNotEmpty()) {
                                    FuelPricesCompactList(prices)
                                } else {
                                    Text(
                                        text = stringResource(R.string.no_fuel_price_details),
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            PoiCategory.Irve -> {
                                availabilitySummary?.let { summary ->
                                    AvailabilitySlotsRow(
                                        summary = summary,
                                        textColor = Color.White.copy(alpha = 0.85f),
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                                val line = listOfNotNull(
                                    poi.operator?.takeIf { it.isNotBlank() },
                                    poi.powerKw?.let { "${it.roundToInt()} kW" },
                                    poi.chargePointCount?.let { n -> pluralStringResource(R.plurals.points_count, n, n) },
                                ).joinToString(" • ")
                                if (line.isNotBlank()) {
                                    val powerKw = poi.powerKw
                                    val powerColor = powerKw?.let { ColorHelper.getPowerColor(it) }
                                    Text(
                                        text = line,
                                        color = powerColor ?: Color.White.copy(alpha = 0.85f),
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                                val connectors = poi.irveDetails?.connectorTypes.orEmpty().sorted()
                                if (connectors.isNotEmpty()) {
                                    Text(
                                        text = stringResource(R.string.connectors_label, connectors.joinToString(", ") { BrandHelper.connectorTypeLabel(it) }),
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 11.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Always show the full list of known fuel prices, even when the user filtered the map
                                // to Electric or a specific fuel type (hybrid stations can have both).
                                val prices = poi.fuelPrices.orEmpty()
                                if (prices.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    FuelPricesCompactList(prices)
                                }
                            }
                            else -> {
                                // For non fuel/IRVE categories, show whatever extra info we have.
                                poi.restaurantDetails?.let { d ->
                                    val r = listOfNotNull(
                                        if (d.isFastFood) stringResource(R.string.amenity_fast_food) else null,
                                        d.brand?.takeIf { it.isNotBlank() },
                                        d.cuisine?.takeIf { it.isNotBlank() }
                                    ).joinToString(" • ")
                                    if (r.isNotBlank()) {
                                        Text(
                                            text = r,
                                            color = Color.White.copy(alpha = 0.85f),
                                            fontSize = 12.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                poi.amenities?.let { d ->
                                    val flags = listOfNotNull(
                                        if (d.open24h == true) stringResource(R.string.amenity_24h) else null,
                                        if (d.restaurant == true) stringResource(R.string.amenity_restaurant) else null,
                                        if (d.shop == true) stringResource(R.string.amenity_shop) else null,
                                        if (d.toilets == true) stringResource(R.string.amenity_toilets) else null,
                                        if (d.carWash == true) stringResource(R.string.amenity_car_wash) else null,
                                        if (d.showers == true) stringResource(R.string.amenity_showers) else null,
                                    ).joinToString(" • ")
                                    if (flags.isNotBlank()) {
                                        Text(
                                            text = flags,
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 11.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (isLoggedIn && onToggleFavorite != null) {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (isFavorite) "Saved" else "Save",
                            tint = if (isFavorite) Color(0xFFEAB308) else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}



@Preview(showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
private fun PoiDetailCardPreview() {
    PoiDetailCard(
        poi = Poi(
            id = "preview",
            name = "Sample Station",
            brand = "Total",
            latitude = 48.8566,
            longitude = 2.3522,
            address = "1 Avenue des Champs-Élysées, 75008 Paris",
            addressLocal = null,
            townLocal = "Paris",
            postcode = "75008",
            countryLocal = "France",
            fuelPrices = emptyList(),
            amenities = null
        ),
        onNavigate = {},
        onLocate = {},
        onShowDetails = {}
    )
}
