package fr.geoking.gaston.ui.map

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import fr.geoking.gaston.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.poi.MapPoiFilter
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.shared.datetime.DateTimeUtils
import fr.geoking.gaston.ui.BrandHelper
import fr.geoking.gaston.ui.ColorHelper

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PoiDetailsFullscreenDialog(
    poi: Poi,
    availabilitySummary: StationAvailabilitySummary? = null,
    highlightedFuelIds: Set<String> = emptySet(),
    highlightedPowerLevels: Set<Int> = emptySet(),
    rating: Int? = null,
    onRate: ((Int) -> Unit)? = null,
    isLoggedIn: Boolean = false,
    isCommunityPoi: Boolean = false,
    onEdit: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    onHide: (() -> Unit)? = null,
    onSuggestCorrection: (() -> Unit)? = null,
    onShowOnMap: ((Poi) -> Unit)? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onNavigate: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    /** When true, renders as full-screen content (for marketing captures) instead of a [Dialog]. */
    embedded: Boolean = false,
) {
    val brandInfo = BrandHelper.getBrandInfo(poi.brand)
    val sources = remember(poi.source) {
        poi.source
            ?.split("+")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?: emptyList()
    }
    val locationSummary = buildList {
        listOf(poi.townLocal, poi.postcode).filter { !it.isNullOrBlank() }.joinToString(", ").takeIf { it.isNotBlank() }?.let { add(it) }
        poi.countryLocal?.takeIf { it.isNotBlank() }?.let { add(it) }
    }.joinToString(", ").takeIf { it.isNotBlank() }
    val streetAddress = poi.addressLocal?.takeIf { it.isNotBlank() } ?: poi.address.takeIf { it.isNotBlank() }

    val addressLines = buildList {
        if (!streetAddress.isNullOrBlank()) add(streetAddress)
        if (!locationSummary.isNullOrBlank() && locationSummary != streetAddress) add(locationSummary)
        if (isEmpty()) add("%.4f, %.4f".format(poi.latitude, poi.longitude))
    }

    val detailContent: @Composable () -> Unit = {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(poi.name.takeIf { it.isNotBlank() } ?: stringResource(R.string.poi_station_details)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.action_close)
                            )
                        }
                    },
                    actions = {
                        onToggleFavorite?.let { toggle ->
                            IconButton(onClick = toggle) {
                                Icon(
                                    painter = painterResource(if (isFavorite) R.drawable.ic_star else R.drawable.ic_star_border),
                                    contentDescription = if (isFavorite) stringResource(R.string.route_remove_favorite) else stringResource(R.string.route_add_favorite),
                                    tint = if (isFavorite) Color(0xFFFACC15) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        onNavigate?.let { navigate ->
                            IconButton(onClick = navigate) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_directions),
                                    contentDescription = stringResource(R.string.navigate)
                                )
                            }
                        }
                        onShowOnMap?.let {
                            IconButton(onClick = { it(poi) }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_map),
                                    contentDescription = stringResource(R.string.action_show_on_map)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp)
                    ) {
                        // Header Info
                        Text(
                            text = poi.name.ifBlank { stringResource(R.string.poi_station_label) },
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        brandInfo?.let {
                            Text(it.displayName, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        addressLines.forEach { line ->
                            Text(line, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                        }

                        if (!poi.isElectric && poi.priceRating != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            val rating = poi.priceRating!!
                            val ratingColor = when {
                                rating >= 7.5 -> Color(0xFF22C55E) // Green
                                rating >= 4.5 -> Color(0xFFFACC15) // Yellow
                                else -> Color(0xFFFF6B6B) // Red
                            }
                            val ratingLabel = when {
                                rating >= 8.5 -> stringResource(R.string.poi_rating_very_cheap)
                                rating >= 7.0 -> stringResource(R.string.poi_rating_cheap)
                                rating >= 4.0 -> stringResource(R.string.poi_rating_average)
                                rating >= 2.0 -> stringResource(R.string.poi_rating_expensive)
                                else -> stringResource(R.string.poi_rating_very_expensive)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = ratingColor.copy(alpha = 0.2f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.poi_rating_format, rating),
                                        color = ratingColor,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = ratingLabel,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        if (poi.isElectric) {
                            listOfNotNull(
                                poi.operator?.takeIf { it.isNotBlank() },
                                if (poi.isOnHighway) stringResource(R.string.highway) else null,
                                poi.chargePointCount?.let { n ->
                                    if (n == 1) stringResource(R.string.poi_charge_point_one) else stringResource(R.string.poi_charge_points, n)
                                },
                                availabilitySummary?.let { s ->
                                    stringResource(R.string.poi_availability, s.availableCount, s.totalCount)
                                }
                            ).joinToString(" • ").takeIf { it.isNotBlank() }?.let { info ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = info,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        // Fuel Prices
                        poi.fuelPrices?.let { prices ->
                            if (prices.isNotEmpty()) {
                                SectionHeader(stringResource(R.string.poi_section_prices))
                                prices.forEach { fp ->
                                    val fuelId = MapPoiFilter.fuelNameToId(fp.fuelName)
                                    val matchColor = fuelId?.let { ColorHelper.getFuelColor(it) }
                                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = fp.fuelName,
                                                color = matchColor ?: MaterialTheme.colorScheme.onSurface,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = if (fp.outOfStock) "—" else "€%.3f".format(fp.price),
                                                color = if (fp.outOfStock) Color.White.copy(alpha = 0.5f) else Color(0xFF22C55E),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                val overallLastUpdate = poi.sourceUpdates?.values?.maxOrNull()
                                    ?: prices.mapNotNull { it.updatedAt }.maxOrNull()
                                overallLastUpdate?.let { timestamp ->
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = stringResource(
                                            R.string.poi_last_updated,
                                            DateTimeUtils.formatRelativeTime(timestamp)
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        modifier = Modifier.align(Alignment.End)
                                    )
                                }
                            }
                        }

                        // IRVE Details
                        if (poi.isElectric && poi.irveDetails != null) {
                            val d = poi.irveDetails!!
                            SectionHeader(stringResource(R.string.poi_label_connectors))
                            val powerKw = poi.powerKw
                            val powerColor = powerKw?.let { ColorHelper.getPowerColor(it) }
                            if (powerKw != null) {
                                Text(
                                    text = stringResource(R.string.power_kw_format, powerKw.toInt()),
                                    color = powerColor ?: Color.White.copy(alpha = 0.9f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            if (d.connectorTypes.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    d.connectorTypes.sorted().forEach { id ->
                                        AssistChip(
                                            onClick = {},
                                            label = { Text(BrandHelper.connectorTypeLabel(id), fontSize = 12.sp) },
                                            colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF475569)),
                                            interactionSource = remember { MutableInteractionSource() }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            if (d.gratuit == true) {
                                Text(
                                    text = stringResource(R.string.poi_free),
                                    color = Color(0xFF22C55E),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            d.tarification?.takeIf { it.isNotBlank() }?.let { text ->
                                PoiDetailRowStr(stringResource(R.string.poi_label_pricing), text)
                            }
                            d.openingHours?.takeIf { it.isNotBlank() }?.let { text ->
                                PoiDetailRowStr(stringResource(R.string.poi_label_hours), text)
                            }
                            if (d.reservation == true) {
                                PoiDetailRow(stringResource(R.string.poi_reservation_possible), true)
                            }
                            listOfNotNull(
                                if (d.paymentActe == true) stringResource(R.string.poi_payment_on_site) else null,
                                if (d.paymentCb == true) stringResource(R.string.poi_payment_card) else null,
                                if (d.paymentAutre == true) stringResource(R.string.poi_payment_other) else null
                            ).joinToString(", ").takeIf { it.isNotBlank() }?.let { pay ->
                                PoiDetailRowStr(stringResource(R.string.poi_label_payment), pay)
                            }
                            d.conditionAcces?.takeIf { it.isNotBlank() }?.let { text ->
                                PoiDetailRowStr(stringResource(R.string.poi_label_access), text)
                            }
                        }

                        // Community Actions
                        if (isLoggedIn && (onEdit != null || onRemove != null || onHide != null || onSuggestCorrection != null)) {
                            SectionHeader(stringResource(R.string.poi_section_actions))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isCommunityPoi) {
                                    onEdit?.let { TextButton(onClick = it) { Text(stringResource(R.string.action_edit), color = Color(0xFF94A3B8), fontSize = 13.sp) } }
                                    onRemove?.let { TextButton(onClick = it) { Text(stringResource(R.string.action_remove), color = Color(0xFFFF6B6B), fontSize = 13.sp) } }
                                } else {
                                    onHide?.let {
                                        TextButton(onClick = it) {
                                            Text(stringResource(R.string.action_hide_on_map), color = Color(0xFF94A3B8), fontSize = 13.sp)
                                        }
                                    }
                                    onSuggestCorrection?.let {
                                        TextButton(onClick = it) {
                                            Text(stringResource(R.string.action_suggest_correction), color = Color(0xFF94A3B8), fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }


                        // Restaurant Details
                        poi.restaurantDetails?.let { d ->
                            SectionHeader(stringResource(R.string.poi_section_restaurant))
                            if (d.isFastFood) {
                                Text(
                                    text = stringResource(R.string.poi_fast_food),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            d.brand?.takeIf { it.isNotBlank() }?.let { text ->
                                PoiDetailRowStr(stringResource(R.string.poi_brand), text)
                            }
                            d.cuisine?.takeIf { it.isNotBlank() }?.let { text ->
                                PoiDetailRowStr(stringResource(R.string.poi_cuisine), text)
                            }
                            d.openingHours?.takeIf { it.isNotBlank() }?.let { text ->
                                PoiDetailRowStr(stringResource(R.string.poi_label_hours), text)
                            }
                        }

                        // Routex Details
                        poi.amenities?.let { details ->
                            SectionHeader(stringResource(R.string.poi_services_amenities))
                            PoiDetailRow(stringResource(R.string.amenity_manned_24h), details.manned24h)
                            PoiDetailRow(stringResource(R.string.amenity_manned_automat_24h), details.mannedAutomat24h)
                            PoiDetailRow(stringResource(R.string.amenity_automat), details.automat)
                            PoiDetailRow(stringResource(R.string.amenity_motorway), details.motorwayIndicator)
                            PoiDetailRow(stringResource(R.string.amenity_restaurant), details.restaurant)
                            PoiDetailRow(stringResource(R.string.amenity_shop), details.shop)
                            PoiDetailRow(stringResource(R.string.amenity_snackbar), details.snackbar)
                            PoiDetailRow(stringResource(R.string.amenity_car_wash), details.carWash)
                            PoiDetailRow(stringResource(R.string.amenity_showers), details.showers)
                            PoiDetailRow(stringResource(R.string.amenity_adblue_pump), details.adBluePump)
                            PoiDetailRow(stringResource(R.string.amenity_r4t_network), details.r4tNetwork)
                            PoiDetailRow(stringResource(R.string.amenity_car_vignette), details.carVignette)
                            PoiDetailRow(stringResource(R.string.amenity_highspeed_diesel), details.highspeedDiesel)
                            PoiDetailRow(stringResource(R.string.amenity_truck_station), details.truckIndicator)
                            PoiDetailRow(stringResource(R.string.amenity_truck_parking), details.truckParking)
                            PoiDetailRow(stringResource(R.string.amenity_truck_diesel), details.truckDiesel)
                            PoiDetailRow(stringResource(R.string.amenity_truck_lane), details.truckLane)
                            PoiDetailRow(stringResource(R.string.amenity_diesel_bio), details.dieselBio)
                            PoiDetailRow(stringResource(R.string.amenity_hvo100), details.hvo100)
                            PoiDetailRow(stringResource(R.string.amenity_lng), details.lng)
                            PoiDetailRow(stringResource(R.string.amenity_lpg), details.lpg)
                            PoiDetailRow(stringResource(R.string.amenity_cng), details.cng)
                            PoiDetailRow(stringResource(R.string.amenity_adblue_canister), details.adBlueCanister)
                            PoiDetailRow(stringResource(R.string.amenity_24h), details.open24h)

                            PoiDetailRow(stringResource(R.string.amenity_toilets), details.toilets)
                            PoiDetailRow(stringResource(R.string.amenity_drinking_water), details.drinkingWater)
                            PoiDetailRow(stringResource(R.string.amenity_food), details.food)
                            PoiDetailRow(stringResource(R.string.poi_amenity_wifi), details.wifi)
                            PoiDetailRow(stringResource(R.string.poi_amenity_atm), details.atm)
                            PoiDetailRow(stringResource(R.string.amenity_playground), details.playground)

                            SectionHeader(stringResource(R.string.poi_fuel_opening_hours))
                            PoiDetailRowStr(stringResource(R.string.day_mon), details.monOpenFuel?.let { o -> details.monCloseFuel?.let { c -> "$o – $c" } ?: o })
                            PoiDetailRowStr(stringResource(R.string.day_tue), details.tueOpenFuel?.let { o -> details.tueCloseFuel?.let { c -> "$o – $c" } ?: o })
                            PoiDetailRowStr(stringResource(R.string.day_wed), details.wedOpenFuel?.let { o -> details.wedCloseFuel?.let { c -> "$o – $c" } ?: o })
                            PoiDetailRowStr(stringResource(R.string.day_thu), details.thuOpenFuel?.let { o -> details.thuCloseFuel?.let { c -> "$o – $c" } ?: o })
                            PoiDetailRowStr(stringResource(R.string.day_fri), details.friOpenFuel?.let { o -> details.friCloseFuel?.let { c -> "$o – $c" } ?: o })
                            PoiDetailRowStr(stringResource(R.string.day_sat), details.satOpenFuel?.let { o -> details.satCloseFuel?.let { c -> "$o – $c" } ?: o })
                            PoiDetailRowStr(stringResource(R.string.day_sun), details.sunOpenFuel?.let { o -> details.sunCloseFuel?.let { c -> "$o – $c" } ?: o })
                            if (details.openingHoursFuel.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                details.openingHoursFuel.forEach { line ->
                                    Text(line, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                                }
                            }
                        }

                        if (sources.isNotEmpty()) {
                            SectionHeader(stringResource(R.string.screen_sources))
                            sources.forEach { s ->
                                val updateTime = poi.sourceUpdates?.get(s)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "• $s",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp
                                    )
                                    if (updateTime != null) {
                                        Text(
                                            text = DateTimeUtils.formatRelativeTime(updateTime),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (embedded) {
        detailContent()
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            detailContent()
        }
    }
}


@Composable
private fun SectionHeader(title: String) {
    Spacer(modifier = Modifier.height(24.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(12.dp))
}
