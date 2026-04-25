package fr.geoking.gaston.intent

import android.content.Intent
import android.net.Uri

data class NavDestination(
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

object IntentNavigationHelper {
    fun parseNavIntent(intent: Intent): NavDestination? {
        val uri = intent.data ?: return null
        val scheme = uri.scheme?.lowercase()
        val ssp = uri.schemeSpecificPart ?: ""

        return when (scheme) {
            "geo" -> parseGeoUri(uri)
            "google.navigation" -> parseGoogleNavUri(uri)
            else -> null
        }
    }

    private fun parseGeoUri(uri: Uri): NavDestination? {
        // geo:lat,lon?q=query
        val ssp = uri.schemeSpecificPart
        val coordsPart = ssp.substringBefore('?')
        val query = if (ssp.contains("q=")) {
            Uri.decode(ssp.substringAfter("q=").substringBefore('&').replace('+', ' '))
        } else null

        var lat: Double? = null
        var lon: Double? = null

        if (coordsPart.isNotEmpty()) {
            val parts = coordsPart.split(',')
            if (parts.size >= 2) {
                lat = parts[0].toDoubleOrNull()
                lon = parts[1].toDoubleOrNull()
            }
        }

        // If q is present, it's often the address. If lat/lon are 0,0, treat q as address.
        if (query != null && (lat == null || lat == 0.0) && (lon == null || lon == 0.0)) {
            return NavDestination(address = query)
        }

        if (lat != null && lon != null) {
            return NavDestination(address = query, latitude = lat, longitude = lon)
        }

        if (query != null) {
            return NavDestination(address = query)
        }

        return null
    }

    private fun parseGoogleNavUri(uri: Uri): NavDestination? {
        // google.navigation:q=lat,lon or google.navigation:q=address
        val ssp = uri.schemeSpecificPart
        val query = if (ssp.contains("q=")) {
            Uri.decode(ssp.substringAfter("q=").substringBefore('&').replace('+', ' '))
        } else {
            null
        } ?: return null

        val parts = query.split(',')
        if (parts.size == 2) {
            val lat = parts[0].toDoubleOrNull()
            val lon = parts[1].toDoubleOrNull()
            if (lat != null && lon != null) {
                return NavDestination(latitude = lat, longitude = lon)
            }
        }

        return NavDestination(address = query)
    }
}
