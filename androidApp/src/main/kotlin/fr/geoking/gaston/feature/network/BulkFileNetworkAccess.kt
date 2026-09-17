package fr.geoking.gaston.feature.network

import fr.geoking.gaston.AppSettings
import fr.geoking.gaston.shared.network.NetworkType

/**
 * Whether bulk file downloads (national CSV/JSON dumps) are allowed on the current network.
 */
object BulkFileNetworkAccess {
    fun allowFetch(settings: AppSettings, networkType: NetworkType): Boolean {
        if (!settings.bulkFileDownloadsWifiOnly) return true
        return networkType == NetworkType.WIFI
    }
}
