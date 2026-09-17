package fr.geoking.gaston.api.belib

import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.feature.network.BulkFileNetworkAccess
import fr.geoking.gaston.shared.network.NetworkService

/**
 * Skips [inner] when Settings → Wi‑Fi only is on and the device is not on Wi‑Fi.
 * Used for availability providers that pull national file dumps.
 */
class WifiGatedBulkAvailabilityProvider(
    private val inner: BorneAvailabilityProvider,
    private val settingsManager: SettingsManager,
    private val networkService: NetworkService,
) : BorneAvailabilityProvider {
    override suspend fun getAvailability(
        latitude: Double,
        longitude: Double,
        radiusKm: Int,
    ): List<PdcAvailability> {
        val settings = settingsManager.settings.value
        val networkType = networkService.status.value.networkType
        if (!BulkFileNetworkAccess.allowFetch(settings, networkType)) {
            return emptyList()
        }
        return inner.getAvailability(latitude, longitude, radiusKm)
    }
}
