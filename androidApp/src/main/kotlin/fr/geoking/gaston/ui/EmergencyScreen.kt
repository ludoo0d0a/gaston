package fr.geoking.gaston.ui

import android.content.Context
import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.ClipData
import android.content.ClipboardManager
import fr.geoking.gaston.feature.emergency.EmergencyCategory
import fr.geoking.gaston.feature.emergency.EmergencyContact
import fr.geoking.gaston.feature.emergency.EmergencyContactRegistry
import fr.geoking.gaston.feature.location.LocationHelper
import fr.geoking.gaston.shared.network.NetworkService
import fr.geoking.gaston.ui.dashboard.GastonTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private val EmergencyRed = Color(0xFFD32F2F)
private val EmergencyRedDark = Color(0xFFB71C1C)
private val EmergencyOnRed = Color.White

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyScreen(
    networkService: NetworkService?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val networkStatus = networkService?.status?.collectAsState()?.value

    var refreshTick by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var address by remember { mutableStateOf<String?>(null) }
    var latLng by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var detectedCountryCode by remember { mutableStateOf<String?>(null) }
    var thoroughfare by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshTick) {
        loading = true
        address = null
        latLng = null
        detectedCountryCode = null
        thoroughfare = null
        val location = withContext(Dispatchers.IO) {
            LocationHelper.getCurrentLocation(context)
        }
        if (location != null) {
            latLng = location.latitude to location.longitude
            val info = withContext(Dispatchers.IO) {
                reverseGeocodeWithMeta(context, location.latitude, location.longitude)
            }
            address = info?.address
            detectedCountryCode = info?.countryCode
            thoroughfare = info?.thoroughfare
        } else {
            address = "Location not available — turn on GPS, or move to an open-sky area."
        }
        loading = false
    }

    val countryCode = detectedCountryCode ?: networkStatus?.countryCode
    val countryName = EmergencyContactRegistry.countryDisplayName(countryCode)
        ?: networkStatus?.countryName
    val contacts = remember(countryCode) {
        EmergencyContactRegistry.contactsFor(countryCode)
    }

    val universalNumber = remember(countryCode) { universalNumberFor(countryCode) }
    val onHighway = remember(thoroughfare) { isLikelyHighway(thoroughfare) }

    GastonTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Emergency") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { refreshTick++ }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh location")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    UniversalEmergencyButton(
                        number = universalNumber,
                        onCall = { dial(context, universalNumber) }
                    )
                }

                item {
                    LocationCard(
                        loading = loading,
                        latLng = latLng,
                        address = address,
                        countryName = countryName,
                        onHighway = onHighway,
                        onCopy = {
                            val payload = buildLocationMessage(latLng, address)
                            if (payload.isNotBlank()) {
                                copyToClipboard(context, payload)
                            }
                        },
                        onShare = {
                            shareLocation(context, latLng, address)
                        },
                        onOpenInMaps = {
                            latLng?.let { (lat, lon) -> openInMaps(context, lat, lon) }
                        }
                    )
                }

                if (contacts.isNotEmpty()) {
                    item {
                        Text(
                            text = "Useful numbers" + (countryName?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        )
                    }
                    items(contacts) { contact ->
                        ContactRow(
                            contact = contact,
                            onCall = { dial(context, contact.number) }
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Tip: when calling 112 from a mobile in the EU, the operator can locate you automatically (AML).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun UniversalEmergencyButton(number: String, onCall: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = EmergencyRed),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    imageVector = Icons.Default.Sos,
                    contentDescription = null,
                    tint = EmergencyOnRed,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "Need help now?",
                    style = MaterialTheme.typography.titleLarge,
                    color = EmergencyOnRed,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Tap to call $number",
                style = MaterialTheme.typography.bodyMedium,
                color = EmergencyOnRed.copy(alpha = 0.95f)
            )
            Button(
                onClick = onCall,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmergencyOnRed,
                    contentColor = EmergencyRedDark
                )
            ) {
                Icon(Icons.Default.Phone, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Call $number",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun LocationCard(
    loading: Boolean,
    latLng: Pair<Double, Double>?,
    address: String?,
    countryName: String?,
    onHighway: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onOpenInMaps: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Your location",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }
            when {
                loading -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Locating you…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                latLng != null -> {
                    Text(
                        text = address ?: "Address unavailable",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val (lat, lon) = latLng
                    Text(
                        text = "Lat: ${"%.6f".format(Locale.US, lat)}, Lon: ${"%.6f".format(Locale.US, lon)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (countryName != null) {
                        Text(
                            text = "Country: $countryName",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (onHighway) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = EmergencyRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "You appear to be on a highway / motorway.",
                                style = MaterialTheme.typography.bodySmall,
                                color = EmergencyRed
                            )
                        }
                    }
                }
                else -> {
                    Text(
                        text = address ?: "Location unavailable",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onShare,
                    enabled = latLng != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Share")
                }
                OutlinedButton(
                    onClick = onOpenInMaps,
                    enabled = latLng != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Open in maps")
                }
            }
            OutlinedButton(
                onClick = onCopy,
                enabled = latLng != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Copy location")
            }
        }
    }
}

@Composable
private fun ContactRow(contact: EmergencyContact, onCall: () -> Unit) {
    val tint = colorForCategory(contact.category)
    Card(
        onClick = onCall,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconForCategory(contact.category),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                if (!contact.description.isNullOrBlank()) {
                    Text(
                        text = contact.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = contact.number,
                style = MaterialTheme.typography.titleMedium,
                color = tint,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = "Call ${contact.label}",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun iconForCategory(category: EmergencyCategory): ImageVector = when (category) {
    EmergencyCategory.GENERAL -> Icons.Default.Sos
    EmergencyCategory.POLICE -> Icons.Default.LocalPolice
    EmergencyCategory.MEDICAL -> Icons.Default.LocalHospital
    EmergencyCategory.FIRE -> Icons.Default.LocalFireDepartment
    EmergencyCategory.ROADSIDE -> Icons.Default.Warning
    EmergencyCategory.OTHER -> Icons.Default.Phone
}

private fun colorForCategory(category: EmergencyCategory): Color = when (category) {
    EmergencyCategory.GENERAL -> EmergencyRed
    EmergencyCategory.POLICE -> Color(0xFF1565C0)
    EmergencyCategory.MEDICAL -> Color(0xFF2E7D32)
    EmergencyCategory.FIRE -> Color(0xFFE65100)
    EmergencyCategory.ROADSIDE -> Color(0xFFEF6C00)
    EmergencyCategory.OTHER -> Color(0xFF6A1B9A)
}

private fun universalNumberFor(countryCode: String?): String {
    val cc = countryCode?.uppercase()
    return when (cc) {
        "US", "CA", "MX" -> "911"
        else -> "112"
    }
}

private fun isLikelyHighway(thoroughfare: String?): Boolean {
    if (thoroughfare.isNullOrBlank()) return false
    val s = thoroughfare.lowercase()
    val keywords = listOf(
        "autoroute", "motorway", "highway", "interstate",
        "autobahn", "autostrada", "autovía", "autopista",
        "snelweg", "freeway"
    )
    if (keywords.any { it in s }) return true
    // French autoroute codes (e.g. "A7", "A104") and US interstate codes ("I-95").
    if (Regex("^[a-z]\\s?\\d{1,4}\\b").containsMatchIn(s)) return true
    if (Regex("^i-\\s?\\d{1,3}\\b").containsMatchIn(s)) return true
    return false
}

private data class GeocodeMeta(val address: String?, val countryCode: String?, val thoroughfare: String?)

private suspend fun reverseGeocodeWithMeta(context: Context, lat: Double, lon: Double): GeocodeMeta? {
    val geocoder = Geocoder(context, Locale.getDefault())
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCoroutine { cont ->
                geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        cont.resume(addresses.firstOrNull()?.toMeta())
                    }

                    override fun onError(errorMessage: String?) {
                        cont.resume(null)
                    }
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()?.toMeta()
            }
        }
    } catch (_: Exception) {
        null
    }
}

private fun Address.toMeta(): GeocodeMeta {
    val sb = StringBuilder()
    for (i in 0..maxAddressLineIndex) {
        if (i > 0) sb.append(", ")
        sb.append(getAddressLine(i))
    }
    val rendered = sb.toString().ifBlank { null }
    return GeocodeMeta(
        address = rendered,
        countryCode = countryCode,
        thoroughfare = thoroughfare ?: featureName
    )
}

private fun buildLocationMessage(latLng: Pair<Double, Double>?, address: String?): String {
    val (lat, lon) = latLng ?: return ""
    val mapsLink = "https://maps.google.com/?q=%.6f,%.6f".format(Locale.US, lat, lon)
    return buildString {
        append("I need help. My current location:")
        append("\n")
        if (!address.isNullOrBlank()) {
            append(address)
            append("\n")
        }
        append("Lat ${"%.6f".format(Locale.US, lat)}, Lon ${"%.6f".format(Locale.US, lon)}")
        append("\n")
        append(mapsLink)
    }
}

private fun shareLocation(context: Context, latLng: Pair<Double, Double>?, address: String?) {
    val text = buildLocationMessage(latLng, address)
    if (text.isBlank()) return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Emergency: my location")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(intent, "Share my location").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}

private fun openInMaps(context: Context, lat: Double, lon: Double) {
    val uri = Uri.parse("geo:%.6f,%.6f?q=%.6f,%.6f".format(Locale.US, lat, lon, lat, lon))
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // No maps app available — fall back to a web URL.
        val webUri = Uri.parse("https://maps.google.com/?q=%.6f,%.6f".format(Locale.US, lat, lon))
        context.startActivity(
            Intent(Intent.ACTION_VIEW, webUri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("Emergency location", text))
}

private fun dial(context: Context, number: String) {
    // Strip any whitespace; the dialer accepts +, *, # and digits.
    val sanitized = number.replace(" ", "")
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$sanitized")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // Device cannot place calls (e.g. tablet) — silently ignore.
    }
}
