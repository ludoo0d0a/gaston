package fr.geoking.gaston.auto.testutil

import fr.geoking.gaston.community.CommunityPoiRepository
import fr.geoking.gaston.poi.Poi

class FakeCommunityPoiRepository : CommunityPoiRepository {
    override suspend fun getCommunityPoisInArea(lat: Double, lng: Double, radiusKm: Double): List<Poi> = emptyList()

    override suspend fun getCommunityLinkedOfficialIdsInArea(lat: Double, lng: Double, radiusKm: Double): Set<String> =
        emptySet()

    override suspend fun getHiddenOfficialIds(): Set<String> = emptySet()

    override suspend fun addCommunityPoi(poi: Poi, linkedOfficialId: String?) {}

    override suspend fun updateCommunityPoi(id: String, poi: Poi) {}

    override suspend fun removeCommunityPoi(id: String) {}

    override suspend fun hideOfficialPoi(externalPoiId: String) {}

    override suspend fun unhideOfficialPoi(externalPoiId: String) {}

    override suspend fun getCommunityPoi(id: String): Poi? = null
}
