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
    onDismiss: () -> Unit
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(poi.name.takeIf { it.isNotBlank() } ?: "Station details") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = "Close"
                            )
                        }
                    },
                    actions = {
                        onToggleFavorite?.let { toggle ->
                            IconButton(onClick = toggle) {
                                Icon(
                                    painter = painterResource(if (isFavorite) R.drawable.ic_star else R.drawable.ic_star_border),
                                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                                    tint = if (isFavorite) Color(0xFFFACC15) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        onNavigate?.let { navigate ->
                            IconButton(onClick = navigate) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_directions),
                                    contentDescription = "Navigate"
                                )
                            }
                        }
                        onShowOnMap?.let {
                            IconButton(onClick = { it(poi) }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_map),
                                    contentDescription = "Show on Map"
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
                            text = poi.name.ifBlank { "Station" },
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
                                rating >= 8.5 -> "Consistently very cheap"
                                rating >= 7.0 -> "Consistently cheap"
                                rating >= 4.0 -> "Average prices"
                                rating >= 2.0 -> "Consistently expensive"
                                else -> "Very expensive"
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = ratingColor.copy(alpha = 0.2f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "%.1f / 10".format(rating),
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
                                if (poi.isOnHighway) "Autoroute" else null,
                                poi.chargePointCount?.let { n ->
                                    if (n == 1) "1 point de charge" else "$n points de charge"
                                },
                                availabilitySummary?.let { s ->
                                    "${s.availableCount} / ${s.totalCount} disponibles"
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
                                SectionHeader("Prices")
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
                                        text = "Last updated ${DateTimeUtils.formatRelativeTime(timestamp)}",
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
                            SectionHeader("Connecteurs")
                            val powerKw = poi.powerKw
                            val powerColor = powerKw?.let { ColorHelper.getPowerColor(it) }
                            if (powerKw != null) {
                                Text(
                                    text = "${powerKw.toInt()} kW",
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
                                    text = "Gratuit",
                                    color = Color(0xFF22C55E),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            d.tarification?.takeIf { it.isNotBlank() }?.let { text ->
                                PoiDetailRowStr("Tarification", text)
                            }
                            d.openingHours?.takeIf { it.isNotBlank() }?.let { text ->
                                PoiDetailRowStr("Horaires", text)
                            }
                            if (d.reservation == true) {
                                PoiDetailRow("Réservation possible", true)
                            }
                            listOfNotNull(
                                if (d.paymentActe == true) "À l'acte" else null,
                                if (d.paymentCb == true) "CB" else null,
                                if (d.paymentAutre == true) "Autre" else null
                            ).joinToString(", ").takeIf { it.isNotBlank() }?.let { pay ->
                                PoiDetailRowStr("Paiement", pay)
                            }
                            d.conditionAcces?.takeIf { it.isNotBlank() }?.let { text ->
                                PoiDetailRowStr("Accès", text)
                            }
                        }

                        // Community Actions
                        if (isLoggedIn && (onEdit != null || onRemove != null || onHide != null || onSuggestCorrection != null)) {
                            SectionHeader("Actions")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isCommunityPoi) {
                                    onEdit?.let { TextButton(onClick = it) { Text("Edit", color = Color(0xFF94A3B8), fontSize = 13.sp) } }
                                    onRemove?.let { TextButton(onClick = it) { Text("Remove", color = Color(0xFFFF6B6B), fontSize = 13.sp) } }
                                } else {
                                    onHide?.let { TextButton(onClick = it) { Text("Hide on map", color = Color(0xFF94A3B8), fontSize = 13.sp) } }
                                    onSuggestCorrection?.let { TextButton(onClick = it) { Text("Suggest correction", color = Color(0xFF94A3B8), fontSize = 13.sp) } }
                                }
                            }
                        }


                        // Restaurant Details
                        poi.restaurantDetails?.let { d ->
                            SectionHeader("Restaurant")
                            if (d.isFastFood) {
                                Text(
                                    text = "Fast food",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            d.brand?.takeIf { it.isNotBlank() }?.let { text ->
                                PoiDetailRowStr("Enseigne", text)
                            }
                            d.cuisine?.takeIf { it.isNotBlank() }?.let { text ->
                                PoiDetailRowStr("Cuisine", text)
                            }
                            d.openingHours?.takeIf { it.isNotBlank() }?.let { text ->
                                PoiDetailRowStr("Horaires", text)
                            }
                        }

                        // Routex Details
                        poi.amenities?.let { details ->
                            SectionHeader("Services & amenities")
                            PoiDetailRow("Manned 24h", details.manned24h)
                            PoiDetailRow("Manned / automat 24h", details.mannedAutomat24h)
                            PoiDetailRow("Automat", details.automat)
                            PoiDetailRow("Motorway", details.motorwayIndicator)
                            PoiDetailRow("Restaurant", details.restaurant)
                            PoiDetailRow("Shop", details.shop)
                            PoiDetailRow("Snackbar", details.snackbar)
                            PoiDetailRow("Car wash", details.carWash)
                            PoiDetailRow("Showers", details.showers)
                            PoiDetailRow("AdBlue pump", details.adBluePump)
                            PoiDetailRow("R4T network", details.r4tNetwork)
                            PoiDetailRow("Car vignette", details.carVignette)
                            PoiDetailRow("High-speed diesel", details.highspeedDiesel)
                            PoiDetailRow("Truck station", details.truckIndicator)
                            PoiDetailRow("Truck parking", details.truckParking)
                            PoiDetailRow("Truck diesel", details.truckDiesel)
                            PoiDetailRow("Truck lane", details.truckLane)
                            PoiDetailRow("Diesel bio", details.dieselBio)
                            PoiDetailRow("HVO100", details.hvo100)
                            PoiDetailRow("LNG", details.lng)
                            PoiDetailRow("LPG", details.lpg)
                            PoiDetailRow("CNG", details.cng)
                            PoiDetailRow("AdBlue canister", details.adBlueCanister)
                            PoiDetailRow("Open 24h", details.open24h)

                            PoiDetailRow("Toilets", details.toilets)
                            PoiDetailRow("Drinking water", details.drinkingWater)
                            PoiDetailRow("Food", details.food)
                            PoiDetailRow("Wifi", details.wifi)
                            PoiDetailRow("ATM", details.atm)
                            PoiDetailRow("Playground", details.playground)

                            SectionHeader("Fuel opening hours")
                            PoiDetailRowStr("Mon", details.monOpenFuel?.let { o -> details.monCloseFuel?.let { c -> "$o – $c" } ?: o })
                            PoiDetailRowStr("Tue", details.tueOpenFuel?.let { o -> details.tueCloseFuel?.let { c -> "$o – $c" } ?: o })
                            PoiDetailRowStr("Wed", details.wedOpenFuel?.let { o -> details.wedCloseFuel?.let { c -> "$o – $c" } ?: o })
                            PoiDetailRowStr("Thu", details.thuOpenFuel?.let { o -> details.thuCloseFuel?.let { c -> "$o – $c" } ?: o })
                            PoiDetailRowStr("Fri", details.friOpenFuel?.let { o -> details.friCloseFuel?.let { c -> "$o – $c" } ?: o })
                            PoiDetailRowStr("Sat", details.satOpenFuel?.let { o -> details.satCloseFuel?.let { c -> "$o – $c" } ?: o })
                            PoiDetailRowStr("Sun", details.sunOpenFuel?.let { o -> details.sunCloseFuel?.let { c -> "$o – $c" } ?: o })
                            if (details.openingHoursFuel.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                details.openingHoursFuel.forEach { line ->
                                    Text(line, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                                }
                            }
                        }

                        if (sources.isNotEmpty()) {
                            SectionHeader("Sources")
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
