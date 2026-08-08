package fr.geoking.gaston.poi

import fr.geoking.gaston.shared.location.haversineKm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A generic wrapper for [PoiProvider] that restricts API requests to a circular geofence.
 * If the search region does not overlap with the geofence, it short-circuits and returns empty results.
 */
class CircularGeofencedPoiProvider(
    private val delegate: PoiProvider,
    private val centerLat: Double,
    private val centerLon: Double,
    private val radiusKm: Double
) : AbstractPoiProvider() {

    override val usageRules = PoiProviderRules(
        circleCenter = Pair(centerLat, centerLon),
        circleRadiusKm = radiusKm
    )

    override fun supportedCategories(): Set<PoiCategory> = delegate.supportedCategories()

    override fun searchFlow(request: PoiSearchRequest): Flow<PoiSearchResult> {
        if (!shouldQuery(request.latitude, request.longitude, request.viewport)) {
            return flow { emit(PoiSearchResult()) }
        }
        return delegate.searchFlow(request)
    }

    override suspend fun searchResult(request: PoiSearchRequest): PoiSearchResult {
        if (!shouldQuery(request.latitude, request.longitude, request.viewport)) {
            return PoiSearchResult()
        }
        return delegate.searchResult(request)
    }

    override suspend fun search(request: PoiSearchRequest): List<Poi> {
        if (!shouldQuery(request.latitude, request.longitude, request.viewport)) {
            return emptyList()
        }
        return delegate.search(request)
    }

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        if (!shouldQuery(latitude, longitude, viewport)) {
            return emptyList()
        }
        return delegate.getGasStations(latitude, longitude, viewport)
    }

    override suspend fun clearCache() {
        delegate.clearCache()
    }
}
