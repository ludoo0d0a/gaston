package fr.geoking.gaston.ui.map

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.poi.MapPoiFilter
import fr.geoking.gaston.poi.Poi
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
    val isMergedPoi = sources.size >= 2
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
            color = Color(0xFF1E293B)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(poi.name.takeIf { it.isNotBlank() } ?: "Station details", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        onToggleFavorite?.let { toggle ->
                            IconButton(onClick = toggle) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                                    tint = if (isFavorite) Color(0xFFFACC15) else Color.White
                                )
                            }
                        }
                        onNavigate?.let { navigate ->
                            IconButton(onClick = navigate) {
                                Icon(
                                    imageVector = Icons.Default.Directions,
                                    contentDescription = "Navigate",
                                    tint = Color.White
                                )
                            }
                        }
                        onShowOnMap?.let {
                            IconButton(onClick = { it(poi) }) {
                                Icon(
                                    imageVector = Icons.Default.Map,
                                    contentDescription = "Show on Map",
                                    tint = Color.White
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
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
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        brandInfo?.let {
                            Text(it.displayName, color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        addressLines.forEach { line ->
                            Text(line, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
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
                                    color = Color.White.copy(alpha = 0.7f),
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
                                                color = matchColor ?: Color.White.copy(alpha = 0.9f),
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
                                    color = Color.White.copy(alpha = 0.9f),
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
                        poi.routexDetails?.let { details ->
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
                                    Text(line, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                                }
                            }
                        }

                        if (isMergedPoi) {
                            SectionHeader("Sources")
                            sources.forEach { s ->
                                Text(
                                    text = "• $s",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 14.sp
                                )
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
    HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = title,
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(12.dp))
}
