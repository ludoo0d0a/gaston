package fr.geoking.gaston.poi

import fr.geoking.gaston.api.routex.radiusKmFromMapViewport
import fr.geoking.gaston.community.CommunityPoiRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Wraps a base [PoiProvider] and merges in community POIs from [CommunityPoiRepository],
 * and filters out hidden official POIs.
 */
class MergedPoiProvider(
    private val base: PoiProvider,
    private val communityRepo: CommunityPoiRepository
) : PoiProvider {

    override fun searchFlow(request: PoiSearchRequest): Flow<PoiSearchResult> {
        return base.searchFlow(request).map { baseResult ->
            val hiddenIds = communityRepo.getHiddenOfficialIds()
            val radiusKm = request.viewport
                ?.let { v ->
                    radiusKmFromMapViewport(
                        centerLat = request.latitude,
                        centerLng = request.longitude,
                        zoom = v.zoom,
                        mapWidthPx = v.mapWidthPx,
                        mapHeightPx = v.mapHeightPx
                    ).coerceIn(5, 50).toDouble()
                } ?: 50.0
            val communityPois = communityRepo.getCommunityPoisInArea(request.latitude, request.longitude, radiusKm)
            val linkedOfficialIds = communityRepo.getCommunityLinkedOfficialIdsInArea(request.latitude, request.longitude, radiusKm)
            val filteredBase = baseResult.pois
                .filter { it.id !in hiddenIds }
                .filter { it.id !in linkedOfficialIds }
            baseResult.copy(pois = filteredBase + communityPois)
        }
    }

    override suspend fun search(request: PoiSearchRequest): List<Poi> {
        val basePois = base.search(request)
        val hiddenIds = communityRepo.getHiddenOfficialIds()
        val radiusKm = request.viewport
            ?.let { v ->
                radiusKmFromMapViewport(
                    centerLat = request.latitude,
                    centerLng = request.longitude,
                    zoom = v.zoom,
                    mapWidthPx = v.mapWidthPx,
                    mapHeightPx = v.mapHeightPx
                ).coerceIn(5, 50).toDouble()
            } ?: 50.0
        val communityPois = communityRepo.getCommunityPoisInArea(request.latitude, request.longitude, radiusKm)
        val linkedOfficialIds = communityRepo.getCommunityLinkedOfficialIdsInArea(request.latitude, request.longitude, radiusKm)
        val filteredBase = basePois
            .filter { it.id !in hiddenIds }
            .filter { it.id !in linkedOfficialIds }
        return filteredBase + communityPois
    }

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        return search(PoiSearchRequest(latitude, longitude, viewport, emptySet()))
    }

    override suspend fun clearCache() {
        base.clearCache()
    }
}
